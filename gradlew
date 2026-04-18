#!/bin/sh
# Gradle wrapper script – Unix/Linux/macOS
set -e

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_PROPS="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"

# Download wrapper jar if absent
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Downloading Gradle wrapper JAR …"
    mkdir -p "$APP_HOME/gradle/wrapper"
    curl -fsSL \
        "https://raw.githubusercontent.com/gradle/gradle/v9.4.0/gradle/wrapper/gradle-wrapper.jar" \
        -o "$WRAPPER_JAR" || {
            echo "ERROR: Failed to download gradle-wrapper.jar" >&2
            exit 1
        }
fi

exec java $JAVA_OPTS \
    -classpath "$WRAPPER_JAR" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
