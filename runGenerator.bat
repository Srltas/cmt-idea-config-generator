@echo off
setlocal

set "SCRIPT_DIR=%~dp0"

echo Building CMT IDEA Config Generator...
call mvn clean package -DskipTests -q -f "%SCRIPT_DIR%pom.xml"
if %ERRORLEVEL% NEQ 0 (
    echo Build failed.
    exit /b %ERRORLEVEL%
)

set "JAR_FILE=%SCRIPT_DIR%target\cmt-idea-config-generator-1.0.0-SNAPSHOT-all.jar"

echo Running generator...
java -jar "%JAR_FILE%" ^
    -c "%SCRIPT_DIR%osgi-app.properties" ^
    -p "%SCRIPT_DIR%..\cubrid-migration" ^
    -o "%SCRIPT_DIR%.." ^
    %*
