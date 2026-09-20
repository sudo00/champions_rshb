FROM python:3.12.6-slim-bookworm

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV PYTHONPATH=/app

COPY backend/api/requirements.txt ./api/requirements.txt
RUN pip install --no-cache-dir -r api/requirements.txt

COPY backend/api ./api

EXPOSE 8000

CMD ["uvicorn", "api.main:app", "--host", "0.0.0.0", "--port", "8000"]
