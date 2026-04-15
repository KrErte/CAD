FROM continuumio/miniconda3:latest
WORKDIR /app
RUN conda install -c conda-forge -y cadquery=2.4 python=3.11 && conda clean -afy
COPY requirements.txt .
RUN pip install --no-cache-dir fastapi==0.115.0 "uvicorn[standard]==0.30.6" pydantic==2.9.2
COPY app.py .
EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
