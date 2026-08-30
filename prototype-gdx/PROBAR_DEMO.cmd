@echo off
setlocal
cd /d "%~dp0.."

set "DEMO_JAR=prototype-gdx\target\CoronaPoker-GDX-Prototype.jar"
set "DEMO_MAVEN=C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd"

if not exist "%DEMO_JAR%" (
    echo Preparando la demo grafica de CoronaPoker...
    if not exist "%DEMO_MAVEN%" (
        echo.
        echo No se encuentra Maven en la instalacion de Apache NetBeans.
        echo Consulta prototype-gdx\README.md para compilar manualmente.
        pause
        exit /b 1
    )
    call "%DEMO_MAVEN%" -B -f prototype-gdx\pom.xml -DskipTests clean package
    if errorlevel 1 (
        echo.
        echo No se pudo compilar la demo.
        pause
        exit /b 1
    )
)

java -jar "%DEMO_JAR%"
if errorlevel 1 (
    echo.
    echo La demo termino con un error. Revisa los mensajes anteriores.
    pause
)
