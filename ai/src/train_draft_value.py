import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.linear_model import LogisticRegression
from sklearn.calibration import calibration_curve
from sklearn.metrics import roc_auc_score, log_loss, brier_score_loss

from dataset import load_oracles_elixir_dataset
from export_onnx import export_model_to_onnx

def train_and_export():
    print("==================================================")
    print("  LoL Draft AI: 5v5 Draft Value Model Training   ")
    print("==================================================")
    
    # 1. Load Dataset
    X, y = load_oracles_elixir_dataset(max_games=4000)
    print(f"Total dataset shape: X={X.shape}, y={y.shape}")
    print(f"Class distribution: Blue wins = {np.mean(y):.2%}, Red wins = {1 - np.mean(y):.2%}")
    
    # 2. Train / Test Split
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42, stratify=y)
    
    # 3. Train Model
    print("Training Logistic Regression Draft Value Model...")
    model = LogisticRegression(C=0.5, max_iter=1000, solver="lbfgs")
    model.fit(X_train, y_train)
    
    # 4. Evaluation
    y_pred_proba = model.predict_proba(X_test)[:, 1]
    
    auc = roc_auc_score(y_test, y_pred_proba)
    logloss = log_loss(y_test, y_pred_proba)
    brier = brier_score_loss(y_test, y_pred_proba)
    
    print("\n---------------- Performance Metrics ----------------")
    print(f"  Test ROC-AUC  : {auc:.4f}")
    print(f"  Test LogLoss  : {logloss:.4f}")
    print(f"  Brier Score   : {brier:.4f}")
    print("-----------------------------------------------------\n")
    
    # 5. Calibration Curve
    prob_true, prob_pred = calibration_curve(y_test, y_pred_proba, n_bins=5)
    print("Probability Calibration Curve (5 bins):")
    print("  Bin Pred Prob  |  True Win Rate")
    for pred_p, true_p in zip(prob_pred, prob_true):
        print(f"     {pred_p:7.2%}     |     {true_p:7.2%}")
        
    # 6. Export to ONNX
    export_model_to_onnx(model, "/workspace/src/main/resources/models/draft_value_model.onnx")
    print("\nTraining and ONNX export complete.")

if __name__ == "__main__":
    train_and_export()
