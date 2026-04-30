@echo off
setlocal
cd /d %~dp0

echo ============================================================
echo  Health-ID Vault - Cross-Platform One-Click Run
echo ============================================================

echo.
echo  Step 1: Detecting Maven...
echo.

:: Try to find Maven in the following order:
:: 1. Maven Wrapper (mvnw.cmd)
:: 2. Local apache-maven-3.9.6 folder
:: 3. System-wide mvn command

set MVN=

if exist "mvnw.cmd" (
    set MVN=mvnw.cmd
    echo Using Maven Wrapper: mvnw.cmd
) else if exist "apache-maven-3.9.6\bin\mvn.cmd" (
    set MVN="apache-maven-3.9.6\bin\mvn.cmd"
    echo Using Local Maven: %MVN%
) else (
    where mvn >nul 2>nul
    if %ERRORLEVEL% equ 0 (
        set MVN=mvn
        echo Using System Maven: mvn
    )
)

if "%MVN%"=="" (
    echo.
    echo  ❌ Error: Maven not found! 
    echo  Please ensure the apache-maven-3.9.6 folder is in the root directory or Maven is installed in your PATH.
    echo.
    pause
    exit /b 1
)

echo.
echo  Step 2: Cleaning and Compiling project...
echo.
call %MVN% clean compile

if %ERRORLEVEL% neq 0 (
    echo.
    echo  ❌ Error: Compilation failed! Please check the errors above.
    echo.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo  Step 3: Launching Health-ID Vault UI...
echo.

call %MVN% javafx:run

if %ERRORLEVEL% neq 0 (
    echo.
    echo  ❌ Error: Application terminated with an error.
    echo.
    pause
)

pause
