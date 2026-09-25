FROM python:3.12.6-slim-bookworm AS runtime

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV PYTHONPATH=/app

COPY backend/api/requirements.lock.txt ./api/requirements.lock.txt
RUN pip install --no-cache-dir -r api/requirements.lock.txt

COPY backend/api/*.py ./api/
COPY backend/catalog ./catalog
ENV WINE_CATALOG_PATH=/app/catalog/catalog.jsonl

EXPOSE 8000

CMD ["uvicorn", "api.main:app", "--host", "0.0.0.0", "--port", "8000"]

FROM runtime AS production
