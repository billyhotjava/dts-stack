FROM node:22-bookworm-slim

WORKDIR /app

COPY source/dts-analytics-webapp/server.mjs /app/server.mjs
COPY source/dts-analytics-webapp/legacy /app/legacy

ENV PORT=3001
ENV DTS_ANALYTICS_API_BASE=http://dts-analytics:3000
ENV DTS_ANALYTICS_WEBAPP_LEGACY_DIR=/app/legacy/frontend_client

EXPOSE 3001

CMD ["node","/app/server.mjs"]

