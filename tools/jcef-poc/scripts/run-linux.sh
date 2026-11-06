#!/usr/bin/env sh
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR=$(find "$SCRIPT_DIR/../build/libs" -maxdepth 1 -name 'coronapoker-jcef-poc-*-linux-x86_64.jar' -print -quit)
exec java --add-modules=jdk.httpserver \
  --add-exports=java.base/java.lang=ALL-UNNAMED \
  --add-exports=java.desktop/sun.awt=ALL-UNNAMED \
  --add-exports=java.desktop/sun.java2d=ALL-UNNAMED \
  -jar "$JAR" "$@"
