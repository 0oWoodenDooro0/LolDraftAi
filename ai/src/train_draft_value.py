import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.linear_model import LogisticRegression
from sklearn.calibration import calibration_curve
from sklearn.metrics import roc_auc_score, log_loss, brier_score_loss

import torch
from dataset import load_oracles_elixir_dataset, load_empirical_dataset
from export_onnx import export_model_to_onnx
from hybrid_draft_model import HybridDraftModel

from lightgbm import LGBMClassifier
from sklearn.neural_network import MLPClassifier

def train_hybrid_model(
    max_games=None,
    epochs=30,
    batch_size=64,
    lr=1e-3,
    weight_decay=1e-4,
    device=None,
):
    """
    Trains the anti-symmetric HybridDraftModel (Objective Stats + Learned Embeddings)
    using PyTorch with CUDA/GPU acceleration.
    """
    if device is None:
        device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Training Hybrid Draft Model with PyTorch on device: {device}...")
    
    X, y = load_empirical_dataset(max_games=max_games, augment_symmetric=True, include_partial_drafts=True)
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42, stratify=y)
    
    train_dataset = torch.utils.data.TensorDataset(
        torch.tensor(X_train, dtype=torch.float32),
        torch.tensor(y_train, dtype=torch.float32),
    )
    test_dataset = torch.utils.data.TensorDataset(
        torch.tensor(X_test, dtype=torch.float32),
        torch.tensor(y_test, dtype=torch.float32),
    )
    
    train_loader = torch.utils.data.DataLoader(train_dataset, batch_size=batch_size, shuffle=True)
    test_loader = torch.utils.data.DataLoader(test_dataset, batch_size=batch_size, shuffle=False)
    
    model = HybridDraftModel(num_champions=174, embedding_dim=16, num_empirical_features=11)
    model.to(device)
    
    criterion = torch.nn.BCEWithLogitsLoss()
    optimizer = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=weight_decay)
    
    for epoch in range(1, epochs + 1):
        model.train()
        total_loss = 0.0
        for batch_x, batch_y in train_loader:
            batch_x, batch_y = batch_x.to(device), batch_y.to(device)
            optimizer.zero_grad()
            logits = model.compute_logits(batch_x).squeeze(-1)
            loss = criterion(logits, batch_y)
            loss.backward()
            optimizer.step()
            total_loss += loss.item()
            
        if epoch % 5 == 0 or epoch == epochs:
            avg_loss = total_loss / len(train_loader)
            print(f"  Epoch [{epoch:02d}/{epochs:02d}] - Loss: {avg_loss:.4f}")
            
    # Evaluation on validation set
    model.eval()
    all_preds = []
    all_targets = []
    with torch.no_grad():
        for batch_x, batch_y in test_loader:
            batch_x = batch_x.to(device)
            probs = model(batch_x)[:, 1].cpu().numpy()
            all_preds.extend(probs)
            all_targets.extend(batch_y.numpy())
            
    all_preds = np.array(all_preds)
    all_targets = np.array(all_targets)
    
    auc = float(roc_auc_score(all_targets, all_preds))
    logloss = float(log_loss(all_targets, all_preds))
    brier = float(brier_score_loss(all_targets, all_preds))
    
    metrics = {
        "roc_auc": auc,
        "log_loss": logloss,
        "brier_score": brier,
        "test_size": len(all_targets),
        "train_size": len(y_train),
        "device": str(device),
    }
    return model, metrics

def train_model(max_games=4000, model_type="hybrid"):
    """
    Trains draft value model (Hybrid PyTorch, LightGBM, or MLP).
    """
    if model_type == "hybrid":
        return train_hybrid_model(max_games=max_games)

    X, y = load_oracles_elixir_dataset(max_games=max_games)
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42, stratify=y)
    
    if model_type == "lightgbm":
        print("Training LightGBM Draft Value Model...")
        model = LGBMClassifier(
            n_estimators=120,
            learning_rate=0.03,
            max_depth=4,
            num_leaves=15,
            min_child_samples=20,
            subsample=0.8,
            colsample_bytree=0.8,
            reg_alpha=0.1,
            reg_lambda=1.0,
            random_state=42,
            verbose=-1,
        )
    elif model_type == "mlp":
        print("Training Deep MLP Draft Value Model...")
        model = MLPClassifier(
            hidden_layer_sizes=(64, 32),
            activation="relu",
            alpha=0.01,
            max_iter=500,
            early_stopping=True,
            random_state=42,
        )
    else:
        print("Training Logistic Regression Model...")
        model = LogisticRegression(C=0.5, max_iter=1000, solver="lbfgs")
        
    model.fit(X_train, y_train)
    y_pred_proba = model.predict_proba(X_test)[:, 1]
    
    auc = float(roc_auc_score(y_test, y_pred_proba))
    logloss = float(log_loss(y_test, y_pred_proba))
    brier = float(brier_score_loss(y_test, y_pred_proba))
    
    metrics = {
        "roc_auc": auc,
        "log_loss": logloss,
        "brier_score": brier,
        "test_size": len(y_test),
        "train_size": len(y_train),
    }
    return model, metrics

def train_and_export(output_paths=[
    "/workspace/src/main/resources/models/draft_value_model.onnx",
    "/workspace/data/draft_value_model.onnx"
]):
    print("==================================================")
    print("  LoL Draft AI: Hybrid BP Draft Value Model Training   ")
    print("==================================================")
    
    model, metrics = train_hybrid_model(max_games=None, epochs=25)
    
    print("\n---------------- Performance Metrics ----------------")
    print(f"  Device        : {metrics.get('device')}")
    print(f"  Train Size    : {metrics['train_size']}")
    print(f"  Test Size     : {metrics['test_size']}")
    print(f"  Test ROC-AUC  : {metrics['roc_auc']:.4f}")
    print(f"  Test LogLoss  : {metrics['log_loss']:.4f}")
    print(f"  Brier Score   : {metrics['brier_score']:.4f}")
    print("-----------------------------------------------------\n")
    
    for path in output_paths:
        export_model_to_onnx(model, path)
    print("\nTraining and ONNX export complete.")
    return model, metrics

if __name__ == "__main__":
    train_and_export()

