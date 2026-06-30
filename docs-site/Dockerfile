FROM node:22-alpine AS build
WORKDIR /app

ENV CI=true
RUN corepack enable && corepack prepare pnpm@latest --activate

# Install dependencies first (better layer caching)
COPY docs-site/package.json docs-site/package-lock.json* ./
RUN npm install --no-audit --no-fund

# Copy source and build
COPY docs-site/ .
RUN npm run build

# Serve with nginx
FROM nginx:alpine
COPY --from=build /app/build /usr/share/nginx/html
COPY docs-site/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
