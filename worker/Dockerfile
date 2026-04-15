FROM python:3.11-slim
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends \
      libgl1 libglu1-mesa libxrender1 libxext6 libsm6 curl \
    && rm -rf /var/lib/apt/lists/*
COPY requirements.txt .
RUN pip install --no-cache-dir cadquery==2.4.0 \
    && pip install --no-cache-dir fastapi==0.115.0 "uvicorn[standard]==0.30.6" pydantic==2.9.2
COPY app.py .
EXPOSE 8000
HEALTHCHECK --interval=10s --retries=5 CMD curl -f http://localhost:8000/health || exit 1
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
