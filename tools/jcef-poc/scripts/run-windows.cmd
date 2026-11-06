@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
for %%J in ("%SCRIPT_DIR%..\build\libs\coronapoker-jcef-poc-*-windows-x86_64.jar") do set "POC_JAR=%%~fJ"
if not defined POC_JAR (
  echo No se encontro el JAR. Ejecuta gradlew.bat clean check jar
  exit /b 1
)
java --add-modules=jdk.httpserver --add-exports=java.base/java.lang=ALL-UNNAMED --add-exports=java.desktop/sun.awt=ALL-UNNAMED --add-exports=java.desktop/sun.java2d=ALL-UNNAMED -jar "%POC_JAR%" %*
