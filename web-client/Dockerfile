# --platform=$BUILDPLATFORM: the Vite output is arch-independent static files,
# so build natively instead of under QEMU (node crashes with SIGILL emulated).
FROM --platform=$BUILDPLATFORM node:22-alpine AS build
WORKDIR /app

ENV CI=true
RUN corepack enable && corepack prepare pnpm@latest --activate

# Install dependencies first (better layer caching)
COPY web-client/package.json web-client/pnpm-lock.yaml* web-client/.npmrc* ./
RUN pnpm install --frozen-lockfile --ignore-scripts

# Copy web-client source and shared components
# src/ is placed at /src so ../src resolves correctly from /app
COPY web-client/ .
COPY src/ /src/

ARG APP_VERSION=dev
ENV VITE_APP_VERSION=$APP_VERSION
RUN pnpm build

# Serve with nginx
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY web-client/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
