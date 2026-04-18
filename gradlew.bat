@echo off
setlocal enabledelayedexpansion

set APP_HOME=%~dp0
set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
set WRAPPER_PROPS=%APP_HOME%gradle\wrapper\gradle-wrapper.properties

if not exist "%WRAPPER_JAR%" (
    echo Downloading Gradle wrapper JAR...
    if not exist "%APP_HOME%gradle\wrapper" mkdir "%APP_HOME%gradle\wrapper"
    powershell -Command "Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/gradle/gradle/v9.4.0/gradle/wrapper/gradle-wrapper.jar' -OutFile '%WRAPPER_JAR%'" || (
        echo ERROR: Failed to download gradle-wrapper.jar
        exit /b 1
    )
)

java %JAVA_OPTS% -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
pause
