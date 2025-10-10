FROM node:20-alpine AS build

WORKDIR /app

# Install pnpm matching the repo's packageManager
RUN corepack enable && corepack prepare pnpm@10.8.0 --activate

# Copy lockfile and manifest first to leverage docker layer caching
COPY pnpm-lock.yaml package.json ./

# Install dependencies without running lifecycle scripts (skip Lefthook, etc.)
RUN pnpm install --frozen-lockfile --ignore-scripts

# Copy the remaining project files and build the production bundle
COPY . .
RUN pnpm build

FROM node:20-alpine AS runtime

RUN apk add --no-cache nginx && \
    rm -f /etc/nginx/http.d/default.conf /etc/nginx/conf.d/default.conf && \
    mkdir -p /usr/share/nginx/html

WORKDIR /usr/share/nginx/html
COPY --from=build /app/dist ./
COPY nginx.conf /etc/nginx/http.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
