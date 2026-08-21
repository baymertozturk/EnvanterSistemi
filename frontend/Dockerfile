# =========================================================================
# Stage 1: Build — Node ile React/Vite uygulamasini derle
# =========================================================================
FROM node:24-alpine AS builder

WORKDIR /build

# Once yalnizca manifest'ler: bagimliliklar degismedikce bu katman onbellekten gelir
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# =========================================================================
# Stage 2: Runtime — sadece uretilen statik dosyalar + nginx
# Node ve node_modules son imaja tasinmaz.
# =========================================================================
FROM nginx:1.27-alpine

COPY --from=builder /build/dist /usr/share/nginx/html
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD wget -qO- http://localhost/ >/dev/null || exit 1
