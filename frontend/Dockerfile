# syntax=docker/dockerfile:1
# Stage 1: Build stage with Node 22 on Alpine
FROM node:22-alpine AS builder

WORKDIR /build

# Copy dependency specifications first to leverage Docker layer caching
COPY package.json package-lock.json ./

# Install dependencies cleanly using lockfile
RUN npm ci

# Copy configuration files and application source
COPY tsconfig*.json vite.config.ts tailwind.config.js postcss.config.js index.html ./
COPY public/ ./public/
COPY src/ ./src/

# Compile production assets (TypeScript validation + Vite PWA bundle)
RUN npm run build

# Stage 2: Minimal, hardened static asset server using Caddy
FROM caddy:2-alpine

# Create unprivileged runtime user and configure directory permissions
RUN adduser -D -u 1001 marginalia && \
    mkdir -p /config/caddy /data/caddy /usr/share/caddy && \
    chown -R marginalia:marginalia /config/caddy /data/caddy /usr/share/caddy

# Copy Caddyfile configuration
COPY Caddyfile /etc/caddy/Caddyfile

# Copy compiled static assets from builder stage
COPY --from=builder --chown=marginalia:marginalia /build/dist /usr/share/caddy

# Switch to unprivileged runtime user
USER marginalia

# Expose HTTP port
EXPOSE 80

# Container healthcheck testing root endpoint
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:80/ || exit 1

# Start Caddy static server
CMD ["caddy", "run", "--config", "/etc/caddy/Caddyfile", "--adapter", "caddyfile"]
