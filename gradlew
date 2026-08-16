#!/bin/sh
#
# Gradle wrapper for ShadeVPN-Android.
#
# The stock wrapper cannot run without gradle/wrapper/gradle-wrapper.jar, which
# is a binary blob. This script works either way:
#
#   * jar present -> delegates to org.gradle.wrapper.GradleWrapperMain, i.e.
#                    behaves exactly like the wrapper Gradle generates.
#   * jar absent  -> downloads the distribution declared in
#                    gradle/wrapper/gradle-wrapper.properties, verifies its
#                    SHA-256, caches it, and execs the real gradle from it.
#
# Run `gradle wrapper --gradle-version 8.11.1` to add the real jar; the first
# branch then takes over and nothing here needs to change.

set -e

APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
PROPS="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

die() {
    echo "gradlew: $*" >&2
    exit 1
}

[ -r "$PROPS" ] || die "cannot read $PROPS"

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD=$(command -v java) || die "no java on PATH and JAVA_HOME is unset"
fi

# ---- Preferred path: the real wrapper jar is present ----------------------
if [ -r "$WRAPPER_JAR" ]; then
    # shellcheck disable=SC2086
    exec "$JAVACMD" $JAVA_OPTS $GRADLE_OPTS \
        -classpath "$WRAPPER_JAR" \
        -Dorg.gradle.appname=gradlew \
        org.gradle.wrapper.GradleWrapperMain "$@"
fi

# ---- Fallback path: bootstrap the distribution ourselves -----------------
prop() {
    sed -e 's/\r$//' "$PROPS" \
        | grep "^[[:space:]]*$1[[:space:]]*=" \
        | head -n 1 \
        | sed -e 's/^[^=]*=[[:space:]]*//'
}

DIST_URL=$(prop distributionUrl | sed -e 's|\\:|:|g')
DIST_SHA=$(prop distributionSha256Sum)
[ -n "$DIST_URL" ] || die "distributionUrl is not set in $PROPS"

DIST_ZIP=$(basename "$DIST_URL")
DIST_NAME=$(echo "$DIST_ZIP" | sed -e 's/\.zip$//')
: "${GRADLE_USER_HOME:=$HOME/.gradle}"
BOOTSTRAP="$GRADLE_USER_HOME/wrapper/bootstrap/$DIST_NAME"

find_gradle() {
    ls "$BOOTSTRAP"/*/bin/gradle 2>/dev/null | head -n 1
}

GRADLE_BIN=$(find_gradle || true)

if [ -z "$GRADLE_BIN" ]; then
    echo "gradlew: no gradle-wrapper.jar, bootstrapping $DIST_NAME" >&2
    mkdir -p "$BOOTSTRAP"
    ZIP_PATH="$BOOTSTRAP/$DIST_ZIP.part"

    if command -v curl >/dev/null 2>&1; then
        curl -fsSL --retry 3 -o "$ZIP_PATH" "$DIST_URL"
    elif command -v wget >/dev/null 2>&1; then
        wget -q -O "$ZIP_PATH" "$DIST_URL"
    else
        die "need curl or wget to bootstrap Gradle"
    fi

    if [ -n "$DIST_SHA" ]; then
        if command -v sha256sum >/dev/null 2>&1; then
            ACTUAL=$(sha256sum "$ZIP_PATH" | cut -d' ' -f1)
        elif command -v shasum >/dev/null 2>&1; then
            ACTUAL=$(shasum -a 256 "$ZIP_PATH" | cut -d' ' -f1)
        else
            ACTUAL=""
            echo "gradlew: no sha256 tool available, skipping verification" >&2
        fi
        if [ -n "$ACTUAL" ] && [ "$ACTUAL" != "$DIST_SHA" ]; then
            rm -f "$ZIP_PATH"
            die "checksum mismatch for $DIST_ZIP (expected $DIST_SHA, got $ACTUAL)"
        fi
    fi

    if command -v unzip >/dev/null 2>&1; then
        unzip -q "$ZIP_PATH" -d "$BOOTSTRAP"
    elif [ -x "${JAVA_HOME:-}/bin/jar" ]; then
        (cd "$BOOTSTRAP" && "$JAVA_HOME/bin/jar" xf "$ZIP_PATH")
    else
        rm -f "$ZIP_PATH"
        die "need unzip or the JDK jar tool to unpack $DIST_ZIP"
    fi

    rm -f "$ZIP_PATH"
    GRADLE_BIN=$(find_gradle || true)
fi

[ -n "$GRADLE_BIN" ] || die "bootstrap failed: no gradle binary under $BOOTSTRAP"
chmod +x "$GRADLE_BIN" 2>/dev/null || true
exec "$GRADLE_BIN" "$@"
