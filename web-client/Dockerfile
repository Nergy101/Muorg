# --platform=$BUILDPLATFORM: the Vite output is arch-independent static files,
# so build natively instead of under QEMU (node crashes with SIGILL emulated).
FROM --platform=$BUILDPLATFORM node:22-alpine AS build
WORKDIR /app

ENV CI=true
RUN corepack enable && corepack prepare pnpm@latest --activate

# Mirror the repo layout (web-client/ + src/ as siblings) instead of placing
# the app at /app and the shared tree at /src: with the shared tree at the
# filesystem root, rolldown-vite fails to load the @shared SFC sub-modules
# (the MageIcon re-export ends up self-referencing and the app crashes on
# boot with a stack overflow).
# Install dependencies first (better layer caching). The ROOT manifest is
# installed too: the shared src/ tree imports @lucide/vue, which is a root
# devDependency, and node resolution from src/ walks up to /app/node_modules —
# without it, vue-tsc fails with "Cannot find module '@lucide/vue'".
COPY package.json pnpm-lock.yaml* ./
RUN pnpm install --frozen-lockfile --ignore-scripts
COPY web-client/package.json web-client/pnpm-lock.yaml* web-client/.npmrc* ./web-client/
RUN cd web-client && pnpm install --frozen-lockfile --ignore-scripts

# Copy web-client source and shared components
COPY web-client/ ./web-client/
COPY src/ ./src/

ARG APP_VERSION=dev
ENV VITE_APP_VERSION=$APP_VERSION
RUN cd web-client && pnpm build

# Serve with nginx
FROM nginx:alpine
COPY --from=build /app/web-client/dist /usr/share/nginx/html
COPY web-client/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
