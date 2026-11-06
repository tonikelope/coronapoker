#!/usr/bin/env sh
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR=$(find "$SCRIPT_DIR/../build/libs" -maxdepth 1 -name 'coronapoker-jcef-poc-*-macos-*.jar' -print -quit)
exec java -XstartOnFirstThread -Djcef.poc.mac.firstThread=true \
  --add-modules=jdk.httpserver \
  --add-exports=java.base/java.lang=ALL-UNNAMED \
  --add-exports=java.desktop/sun.awt=ALL-UNNAMED \
  --add-exports=java.desktop/sun.java2d=ALL-UNNAMED \
  --add-opens=java.desktop/sun.awt=ALL-UNNAMED \
  --add-opens=java.desktop/sun.lwawt=ALL-UNNAMED \
  --add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED \
  -jar "$JAR" "$@"
