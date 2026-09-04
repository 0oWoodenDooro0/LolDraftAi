import os
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType

def export_model_to_onnx(model, output_path="/workspace/src/main/resources/models/draft_value_model.onnx"):
    """
    Exports a trained model (LightGBM or scikit-learn) to standard ONNX format.
    Disables zipmap so probabilities are exported as numeric float tensors.
    """
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    options = {id(model): {"zipmap": False}}
    
    print(f"Exporting model to ONNX: {output_path}...")
    
    import torch
    if isinstance(model, torch.nn.Module):
        model.eval()
        # Ensure CPU model for export
        cpu_model = model.to("cpu")
        num_features = getattr(model, "num_features", 21)
        dummy_input = torch.zeros(1, num_features, dtype=torch.float32)
        torch.onnx.export(
            cpu_model,
            dummy_input,
            output_path,
            export_params=True,
            opset_version=15,
            do_constant_folding=True,
            input_names=["float_input"],
            output_names=["probabilities"],
            dynamic_axes={
                "float_input": {0: "batch_size"},
                "probabilities": {0: "batch_size"}
            }
        )
        print(f"ONNX model successfully saved ({os.path.getsize(output_path)} bytes).")
        return output_path

    model_type = type(model).__name__.lower()
    
    if "lgbm" in model_type or "lightgbm" in model_type:
        from onnxmltools import convert_lightgbm
        from onnxmltools.convert.common.data_types import FloatTensorType
        initial_type = [("float_input", FloatTensorType([None, 52]))]
        onnx_model = convert_lightgbm(model, initial_types=initial_type, target_opset=15, zipmap=False)
    else:
        from skl2onnx import convert_sklearn
        from skl2onnx.common.data_types import FloatTensorType
        initial_type = [("float_input", FloatTensorType([None, 52]))]
        onnx_model = convert_sklearn(model, initial_types=initial_type, target_opset=15, options=options)
    
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
