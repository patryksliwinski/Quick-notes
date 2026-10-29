#!/bin/sh
# Gradle wrapper bootstrap script (POSIX sh).
APP_HOME=$(cd "$(dirname "$0")" && pwd)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$CLASSPATH" ]; then
  echo "gradle-wrapper.jar not found; downloading it..."
  mkdir -p "$APP_HOME/gradle/wrapper"
  URL="https://raw.githubusercontent.com/gradle/gradle/v8.10.2/gradle/wrapper/gradle-wrapper.jar"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$URL" -o "$CLASSPATH" || exit 1
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$URL" -O "$CLASSPATH" || exit 1
  else
    echo "Need curl or wget to fetch gradle-wrapper.jar" >&2; exit 1
  fi
fi
exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
