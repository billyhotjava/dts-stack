ARG BASE_IMAGE_NODE=node:22-bookworm-slim
FROM ${BASE_IMAGE_NODE}

WORKDIR /app

# Copy runtime server and legacy UI assets extracted from metabase.jar
COPY server.mjs ./server.mjs
COPY legacy ./legacy

ENV NODE_ENV=production
ENV PORT=3001

EXPOSE 3001

CMD ["node", "server.mjs"]
