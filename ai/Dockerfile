FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /workspace/ai

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    curl \
    && rm -rf /var/lib/apt/lists/*

RUN pip install --upgrade pip setuptools wheel && \
    pip install \
    "numpy>=1.26.0" \
    "scipy>=1.12.0" \
    "pandas>=2.2.0" \
    "scikit-learn>=1.5.0" \
    "skl2onnx>=1.17.0" \
    "onnx>=1.16.0" \
    "onnxruntime>=1.19.0" \
    "pytest>=8.0.0"

COPY . /workspace/ai

CMD ["python", "src/train_draft_value.py"]
