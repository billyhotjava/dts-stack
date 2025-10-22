# Allow overriding Node base from internal mirror
# Alpine tags for Node.js stopped publishing the floating `20-alpine` tag; pin to a known patch.
ARG BASE_IMAGE_NODE=node:20.17.0-alpine3.20
FROM ${BASE_IMAGE_NODE} AS build

WORKDIR /app

# Install pnpm matching the repo's packageManager. Corepack bundled with Node 20.17 lacks the latest signing key,
# so install via npm to avoid signature verification failures.
RUN npm install -g pnpm@10.8.0 && pnpm --version

# Disable opening visualizer report during Docker build
ENV VISUALIZER_OPEN=false

# Copy lockfile and manifest first to leverage docker layer caching
COPY pnpm-lock.yaml package.json ./

# Install dependencies without running lifecycle scripts (skip lefthook, etc.)
RUN pnpm install --frozen-lockfile --ignore-scripts

# Copy the remaining project files and build the production bundle
COPY . .
RUN pnpm build

ARG BASE_IMAGE_NODE=node:20.17.0-alpine3.20
FROM ${BASE_IMAGE_NODE} AS runtime

RUN apk add --no-cache nginx gettext && \
    rm -f /etc/nginx/http.d/default.conf /etc/nginx/conf.d/default.conf && \
    mkdir -p /usr/share/nginx/html

WORKDIR /usr/share/nginx/html
COPY --from=build /app/dist ./

# Template-based Nginx config rendered at container start
COPY nginx.conf.template /etc/nginx/http.d/default.conf.template
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

EXPOSE 80

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["nginx", "-g", "daemon off;"]
