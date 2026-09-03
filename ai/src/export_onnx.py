import os
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType

def export_model_to_onnx(model, output_path="/workspace/src/main/resources/models/draft_value_model.onnx"):
    """
    Exports a trained scikit-learn model to standard ONNX format.
    """
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    initial_type = [('float_input', FloatTensorType([None, 52]))]
    
    print(f"Exporting model to ONNX: {output_path}...")
    onnx_model = convert_sklearn(model, initial_types=initial_type, target_opset=15)
    
    with open(output_path, "wb") as f:
        f.write(onnx_model.SerializeToString())
        
    print(f"ONNX model successfully saved ({os.path.getsize(output_path)} bytes).")
    return output_path

if __name__ == "__main__":
    from sklearn.linear_model import LogisticRegression
    import numpy as np
    
    dummy_model = LogisticRegression()
    dummy_X = np.random.randn(20, 52).astype(np.float32)
    dummy_y = np.random.randint(0, 2, size=20)
    dummy_model.fit(dummy_X, dummy_y)
    
    export_model_to_onnx(dummy_model)
