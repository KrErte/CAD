FROM python:3.14-slim

# CadQuery 2.4 / VTK 9.2 need OSMesa + font stack for offscreen rendering.
RUN apt-get update && apt-get install -y --no-install-recommends \
      curl libosmesa6 libgl1 libxi6 libxmu6 libxrender1 libxext6 \
      libfontconfig1 libfreetype6 libgomp1 libglu1-mesa libxkbcommon0 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# IMPORTANT: numpy must stay <1.24 — cadquery 2.4 drags in old nptyping which
# references numpy.bool8 (removed in 1.24).
COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir -r requirements.txt

COPY app.py test_app.py evolve.py freeform.py printflow.py dfm.py test_dfm.py observability.py ./
EXPOSE 8000
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=10 \
  CMD curl -fs http://localhost:8000/health || exit 1
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
