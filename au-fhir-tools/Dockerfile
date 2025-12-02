FROM node:lts-alpine AS builder

WORKDIR /app

COPY package*.json ./
RUN npm ci
COPY . .

RUN npm run build

FROM node:lts-alpine AS production

WORKDIR /app

COPY package*.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY --from=builder /app/dist ./dist

RUN addgroup -g 1001 -S nodejs && \
    adduser -S nodejs -u 1001
USER nodejs

ENV PORT=8000
EXPOSE 8000

CMD ["npm", "run", "start-sse"]
