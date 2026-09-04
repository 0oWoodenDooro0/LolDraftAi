import pytest
import numpy as np
import sys
import os
import onnxruntime as ort

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "../src"))

import export_onnx
import train_draft_value
from lightgbm import LGBMClassifier


def test_export_lightgbm_onnx_with_probabilities_tensor():
    """Verify that LightGBM can be exported to ONNX without zipmap, providing numeric probability outputs."""
    np.random.seed(42)
    X = np.random.randn(100, 52).astype(np.float32)
    y = np.random.randint(0, 2, size=100)
    
    clf = LGBMClassifier(n_estimators=10, max_depth=3, random_state=42, verbose=-1)
    clf.fit(X, y)
    
    test_onnx_path = "/tmp/test_lgbm_model.onnx"
    export_onnx.export_model_to_onnx(clf, test_onnx_path)
    
    assert os.path.exists(test_onnx_path), "Exported ONNX file must exist"
    assert os.path.getsize(test_onnx_path) > 100, "ONNX file must not be empty"
    
    sess = ort.InferenceSession(test_onnx_path)
    outputs = sess.get_outputs()
    output_names = [o.name for o in outputs]
    
    # Check that probabilities output is a float tensor
    prob_output = next((o for o in outputs if "prob" in o.name.lower()), outputs[-1])
    assert prob_output.type.startswith("tensor(float"), f"Expected float tensor probabilities, got {prob_output.type}"
    
    # Run inference on a 2-sample batch
    sample_input = X[:2]
    preds = sess.run(None, {"float_input": sample_input})
    
    # Find the probability array from preds
    probs = preds[output_names.index(prob_output.name)]
    assert isinstance(probs, np.ndarray)
    assert probs.shape == (2, 2)
    assert np.all(probs >= 0.0) and np.all(probs <= 1.0)
    assert np.allclose(probs.sum(axis=1), 1.0, atol=1e-4)


def test_export_pytorch_hybrid_model_to_onnx():
    """Verify that PyTorch HybridDraftModel exports cleanly to ONNX and produces [1-p, p] output."""
    import torch
    from hybrid_draft_model import HybridDraftModel
    
    model = HybridDraftModel(num_champions=174, embedding_dim=16, num_empirical_features=11)
    test_onnx_path = "/tmp/test_pytorch_hybrid.onnx"
    export_onnx.export_model_to_onnx(model, test_onnx_path)
    
    assert os.path.exists(test_onnx_path)
    assert os.path.getsize(test_onnx_path) > 100
    
    sess = ort.InferenceSession(test_onnx_path)
    dummy_input = np.zeros((2, 21), dtype=np.float32)
    dummy_input[0, 0:5] = [1, 2, 3, 4, 5]
    dummy_input[0, 5:10] = [6, 7, 8, 9, 10]
    dummy_input[1, 0:5] = [6, 7, 8, 9, 10]
    dummy_input[1, 5:10] = [1, 2, 3, 4, 5]
    
    res = sess.run(None, {"float_input": dummy_input})
    probs = res[0]
    assert probs.shape == (2, 2)
    assert np.allclose(probs.sum(axis=1), 1.0, atol=1e-4)
    # Check exact anti-symmetry between sample 0 and sample 1
    assert abs(probs[0, 1] + probs[1, 1] - 1.0) < 1e-4



def test_train_draft_value_pipeline():
    """Verify that the training function executes and returns trained model with valid metrics."""
    model, metrics = train_draft_value.train_model(max_games=300)
    assert model is not None
    assert "roc_auc" in metrics
    assert "brier_score" in metrics
    assert metrics["roc_auc"] >= 0.52, f"Expected ROC-AUC >= 0.52, got {metrics['roc_auc']}"
    assert metrics["brier_score"] < 0.28, f"Expected Brier score < 0.28, got {metrics['brier_score']}"
