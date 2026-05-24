#!/bin/sh
# Gradle wrapper start script
APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
MAX_FD="maximum"
warn () { echo "$*"; }
die () { echo "$*"; exit 1; }
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
if [ -f "$CLASSPATH" ]; then
    eval exec "\"$JAVA_HOME/bin/java\"" $DEFAULT_JVM_OPTS -classpath "\"$CLASSPATH\"" org.gradle.wrapper.GradleWrapperMain "$@"
else
    echo "Gradle wrapper jar not found. Running Gradle directly..."
    GRADLE_VERSION=$(cat gradle/wrapper/gradle-wrapper.properties | grep distributionUrl | sed 's/.*gradle-\([0-9.]*\)-.*/\1/')
    which gradle 2>/dev/null && exec gradle "$@" || die "No Gradle installation found"
fi
