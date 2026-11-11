# famy-web: build de Angular servido por Nginx sin privilegios (puerto 8080).
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration production

FROM nginxinc/nginx-unprivileged:1.29-alpine
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY docker/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY docker/40-famy-config.sh /docker-entrypoint.d/40-famy-config.sh
COPY --from=build --chown=101:101 /app/dist/famy-web/browser /usr/share/nginx/html
ENV FAMY_API_URL="" GOOGLE_CLIENT_ID_WEB=""
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
