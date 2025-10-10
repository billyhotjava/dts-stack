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

WORKDIR /app
RUN npm install -g serve@14.2.1

COPY --from=build /app/dist ./dist

EXPOSE 80

CMD ["serve", "-s", "dist", "-l", "0.0.0.0:80"]
