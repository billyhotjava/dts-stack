#
# dts-analytics-webapp
# - Serves Metabase legacy UI assets via a small Node.js server that injects bootstrap JSON and auth bridge.
# - Keep it minimal (no package installs / no apt).
#

# Allow overriding Node base from internal mirror
ARG BASE_IMAGE_NODE=node:20.17.0-alpine3.20

FROM ${BASE_IMAGE_NODE} AS build

WORKDIR /app

# Recommended build context: repository root
#   docker build -t dts-analytics-webapp:TAG -f builds/dts-analytics-webapp/Dockerfile .
# Copy runtime server and legacy UI assets extracted from metabase.jar
COPY source/dts-analytics-webapp/server.mjs ./server.mjs
COPY source/dts-analytics-webapp/legacy ./legacy

# Fail fast if the server script has syntax errors
RUN node --check /app/server.mjs

FROM ${BASE_IMAGE_NODE} AS runtime

WORKDIR /app

COPY --from=build /app/server.mjs ./server.mjs
COPY --from=build /app/legacy ./legacy

ENV NODE_ENV=production
ENV PORT=3001

EXPOSE 3001

CMD ["node", "server.mjs"]
