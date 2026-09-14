FROM node:22-alpine AS builder

WORKDIR /app

COPY package.json nuxt.config.ts tsconfig.json eslint.config.mjs ./
COPY app ./app
COPY server ./server
COPY public ./public
COPY server/tsconfig.json ./server/tsconfig.json

RUN npm install && npm run build

FROM node:22-alpine

WORKDIR /app

ENV NODE_ENV=production
ENV NITRO_HOST=0.0.0.0
ENV NITRO_PORT=3000
ENV HOST=0.0.0.0
ENV PORT=3000

COPY --from=builder /app/.output ./.output

EXPOSE 3000

CMD ["node", ".output/server/index.mjs"]
