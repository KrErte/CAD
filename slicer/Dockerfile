FROM ubuntu:26.04

ENV DEBIAN_FRONTEND=noninteractive \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    # PrusaSlicer CLI in headless containers needs a fake X server.
    SLICER_BIN="xvfb-run -a prusa-slicer"

# PrusaSlicer from the Ubuntu repos is deliberately headless-friendly, but the
# binary still opens an X connection before accepting CLI flags, so we ship
# xvfb and wrap every call in xvfb-run.
RUN apt-get update && apt-get install -y --no-install-recommends \
      prusa-slicer \
      xvfb \
      xauth \
      libgl1 \
      libglu1-mesa \
      libegl1 \
      libgtk-3-0 \
      libwebkit2gtk-4.0-37 \
      fonts-dejavu-core \
      python3 \
      python3-pip \
      curl \
      ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip3 install --no-cache-dir -r requirements.txt

COPY app.py observability.py ./
COPY profiles ./profiles

# Slicing + gcode export are CPU-bound; default uvicorn single worker is fine
# (horizontal scaling happens at the Deployment level).
EXPOSE 8100
HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=5 \
  CMD curl -fs http://localhost:8100/health || exit 1

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8100"]
