FROM node:20-alpine AS build

WORKDIR /app

# Install pnpm to align with the project toolchain
RUN corepack enable && corepack prepare pnpm@10.8.0 --activate

# Disable opening visualizer report during Docker build
ENV VISUALIZER_OPEN=false

# Copy lockfile and manifest first to leverage docker layer caching
COPY pnpm-lock.yaml package.json ./

# Install dependencies without running lifecycle scripts (skip lefthook, etc.)
RUN pnpm install --frozen-lockfile --ignore-scripts

# Copy the remaining project files and build the production bundle
COPY . .
RUN pnpm build

FROM node:20-alpine AS runtime

RUN apk add --no-cache nginx gettext

WORKDIR /usr/share/nginx/html
COPY --from=build /app/dist ./

# Template-based Nginx config rendered at container start
COPY nginx.conf.template /etc/nginx/conf.d/default.conf.template
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

EXPOSE 80

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["nginx", "-g", "daemon off;"]
