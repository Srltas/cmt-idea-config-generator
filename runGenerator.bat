@echo off
rem
rem Generates the IntelliJ IDEA configuration for a CUBRID Migration Toolkit checkout.
rem
rem   runGenerator.bat [<cmt-project-dir>] [generator options...]
rem
rem The project directory is taken from the first argument, then %CMT_HOME%, then a
rem sibling folder that looks like a CMT checkout. Everything else is derived: the
rem Eclipse bundles come from the Maven p2 cache, so build the CMT project once
rem before running this.

setlocal EnableDelayedExpansion

set "SCRIPT_DIR=%~dp0"
set "MARKER=plugins\com.cubrid.cubridmigration.app"
set "CMT="
set "ARGS="

rem A first argument that does not start with '-' is the project directory.
set "FIRST_ARG=%~1"
if defined FIRST_ARG if not "!FIRST_ARG:~0,1!"=="-" (
    set "CMT=%~1"
    shift
)

rem Collect the rest by hand: %* would ignore the shift above.
:collect
if "%~1"=="" goto :collected
set "ARGS=!ARGS! %1"
shift
goto :collect
:collected

if not defined CMT if defined CMT_HOME set "CMT=%CMT_HOME%"

if not defined CMT (
    for %%c in ("%SCRIPT_DIR%..\cubrid-migration" "%SCRIPT_DIR%..\develop" "%SCRIPT_DIR%..") do (
        if not defined CMT if exist "%%~fc\%MARKER%" set "CMT=%%~fc"
    )
)

if not defined CMT goto :notfound
if not exist "%CMT%\%MARKER%" goto :notfound
for %%p in ("%CMT%") do set "CMT=%%~fp"

echo Building CMT IDEA Config Generator...
call mvn clean package -DskipTests -q -f "%SCRIPT_DIR%pom.xml"
if %ERRORLEVEL% NEQ 0 (
    echo Build failed.
    exit /b %ERRORLEVEL%
)

echo Running generator for %CMT% ...
java -jar "%SCRIPT_DIR%target\cmt-idea-config-generator-1.0.0-SNAPSHOT-all.jar" "%CMT%"!ARGS!
exit /b %ERRORLEVEL%

:notfound
echo CMT project not found.
echo Usage: %~nx0 [^<cmt-project-dir^>] [generator options...]
exit /b 1
