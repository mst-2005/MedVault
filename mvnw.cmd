@echo off
@REM ----------------------------------------------------------------------------
@REM Health-ID Vault - Robust Maven Wrapper Proxy (Windows)
@REM ----------------------------------------------------------------------------

set "LOCAL_MVN=%~dp0apache-maven-3.9.6\bin\mvn.cmd"

if exist "%LOCAL_MVN%" (
    call %LOCAL_MVN% %*
) else (
    mvn %*
)
