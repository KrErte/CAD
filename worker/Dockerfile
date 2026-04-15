FROM mambaorg/micromamba:1.5.10
USER root
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
USER $MAMBA_USER
WORKDIR /app

# CadQuery from conda-forge (pulls in OCP + OSMesa correctly)
RUN micromamba install -y -n base -c conda-forge \
      python=3.11 cadquery=2.4 fastapi=0.115 uvicorn=0.30 pydantic=2.9 \
      httptools uvloop \
    && micromamba clean --all --yes

ARG MAMBA_DOCKERFILE_ACTIVATE=1
COPY --chown=$MAMBA_USER:$MAMBA_USER app.py .
EXPOSE 8000
HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=10 \
  CMD curl -fs http://localhost:8000/health || exit 1
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
