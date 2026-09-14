#!/bin/sh
set -e
cd /app

if [ ! -d node_modules/nuxt ]; then
  echo "node_modules отсутствуют. Запустите: make install-app"
  exec sleep infinity
fi

exec npm run dev
