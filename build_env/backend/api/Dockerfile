FROM python:3.12.6-slim-bookworm

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

COPY backend/api/requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY backend/api/. .

EXPOSE 4000

CMD ["gunicorn", "--bind", "0.0.0.0:4000", "--workers", "2", "app:app"]
