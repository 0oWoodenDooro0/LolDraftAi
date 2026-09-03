import os
import numpy as np
import onnxruntime as rt
from sklearn.metrics import roc_auc_score, log_loss, brier_score_loss
from dataset import load_oracles_elixir_dataset

def evaluate_onnx_model(model_path="/workspace/src/main/resources/models/draft_value_model.onnx"):
    print("==================================================")
    print("   Evaluating ONNX Model via ONNX Runtime        ")
    print("==================================================")
    
    if not os.path.exists(model_path):
        print(f"Error: ONNX model not found at {model_path}")
        return
        
    session = rt.InferenceSession(model_path)
    input_name = session.get_inputs()[0].name
    
    X, y = load_oracles_elixir_dataset(max_games=1000)
    print(f"Evaluating on {len(X)} test samples...")
    
    # Run ONNX inference
    outputs = session.run(None, {input_name: X.astype(np.float32)})
    
    # Check output structure (skl2onnx output: [probabilities_dict_or_tensor, labels])
    prob_output = outputs[1]
    if isinstance(prob_output, list) and isinstance(prob_output[0], dict):
        y_prob = np.array([p[1] for p in prob_output])
    elif isinstance(prob_output, np.ndarray) and prob_output.ndim == 2:
        y_prob = prob_output[:, 1]
    else:
        y_prob = outputs[0].flatten()
        
    auc = roc_auc_score(y, y_prob)
    loss = log_loss(y, y_prob)
    brier = brier_score_loss(y, y_prob)
    
    print("\n---------------- ONNX Inference Report ----------------")
    print(f"  Model File : {model_path}")
    print(f"  ROC-AUC    : {auc:.4f}")
    print(f"  LogLoss    : {loss:.4f}")
    print(f"  Brier Score: {brier:.4f}")
    print("------------------------------------------------------\n")
    print("Evaluation successful!")

if __name__ == "__main__":
    evaluate_onnx_model()
