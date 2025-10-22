# Allow overriding Node base from internal mirror
# Alpine tags for Node.js are no longer published under the rolling `20-alpine`.
# Pin to the current LTS patch to avoid registry lookup failures.
ARG BASE_IMAGE_NODE=node:20.17.0-alpine3.20
FROM ${BASE_IMAGE_NODE} AS build

WORKDIR /app

# Install pnpm matching the repo's packageManager. Corepack bundled with Node 20.17 lacks the new signing key,
# so install via npm to avoid signature verification failures.
RUN npm install -g pnpm@10.8.0 && pnpm --version

# Copy lockfile and manifest first to leverage docker layer caching
COPY pnpm-lock.yaml package.json ./

# Install dependencies without running lifecycle scripts (skip Lefthook, etc.)
RUN pnpm install --frozen-lockfile --ignore-scripts

# Copy the remaining project files and build the production bundle
COPY . .
RUN pnpm build

ARG BASE_IMAGE_NODE=node:20.17.0-alpine3.20
FROM ${BASE_IMAGE_NODE} AS runtime

RUN apk add --no-cache nginx && \
    rm -f /etc/nginx/http.d/default.conf /etc/nginx/conf.d/default.conf && \
    mkdir -p /usr/share/nginx/html

WORKDIR /usr/share/nginx/html
COPY --from=build /app/dist ./
COPY nginx.conf /etc/nginx/http.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
