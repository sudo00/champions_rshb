FROM node:22.9.0-alpine AS builder

WORKDIR /app

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/. .
RUN npm run build

FROM nginx:1.27.2-alpine

COPY --from=builder /app/dist /usr/share/nginx/html
COPY build_env/frontend/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
