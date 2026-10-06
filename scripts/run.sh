#!/usr/bin/env bash
cd "$(dirname "$0")/.."
if [ ! -f "out/com/netapps/httpserver/server/HttpServer.class" ]; then
    echo "Binaries not found. Running build first..."
    bash scripts/build.sh
fi
echo "Starting NetApps HTTP Server..."
java -cp out com.netapps.httpserver.server.HttpServer "$@"
