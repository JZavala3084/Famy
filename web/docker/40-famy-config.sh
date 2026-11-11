#!/bin/sh
# Genera /config.json a partir de variables de entorno al arrancar el contenedor.
set -eu
cat > /usr/share/nginx/html/config.json <<JSON
{
  "apiUrl": "${FAMY_API_URL:-}",
  "googleClientId": "${GOOGLE_CLIENT_ID_WEB:-}"
}
JSON
