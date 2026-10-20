#!/usr/bin/env bash
#
# Builds a signed release APK of LiftBook and puts it in dist/.
#
#   scripts/build-release.sh            build
#   scripts/build-release.sh --install  build, then install it on the connected phone
#
# Signing reads keystore.properties in the repository root (README, "Signed release builds").
# Without one, this script creates a signing key and writes the file, asking for a password —
# or reading LIFTBOOK_KEYSTORE_PASSWORD where there is no terminal. Keep the keystore and the
# file somewhere safe: every later update must be signed with the same key, or the phone
# refuses to install it over the old one.
set -euo pipefail

cd "$(dirname "$0")/.."
root=$(pwd)
install=false
for arg in "$@"; do
    case "$arg" in
        --install) install=true ;;
        -h|--help) sed -n '3,12p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Unknown option: $arg" >&2; exit 2 ;;
    esac
done

# --- Java ---------------------------------------------------------------------------------------
# Gradle needs a JDK. Android Studio ships one; fall back to it when nothing else is set up.
if [ -z "${JAVA_HOME:-}" ] && ! command -v java >/dev/null 2>&1; then
    for candidate in /opt/android-studio/jbr "$HOME/android-studio/jbr" /snap/android-studio/current/jbr \
        "/Applications/Android Studio.app/Contents/jbr/Contents/Home"; do
        if [ -x "$candidate/bin/java" ]; then
            export JAVA_HOME="$candidate"
            break
        fi
    done
fi
if [ -z "${JAVA_HOME:-}" ] && ! command -v java >/dev/null 2>&1; then
    echo "No JDK found. Install one, or set JAVA_HOME (Android Studio's is its jbr folder)." >&2
    exit 1
fi
# keytool lives beside java. On Windows, java may be on the PATH through a launcher folder that
# has no keytool, so ask java where its home is.
find_keytool() {
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/keytool" ]; then
        echo "$JAVA_HOME/bin/keytool"
        return
    fi
    if command -v keytool >/dev/null 2>&1; then
        command -v keytool
        return
    fi
    local home
    home=$(java -XshowSettings:properties -version 2>&1 | sed -nE 's/^ *java\.home = (.*)$/\1/p' | head -1 | tr -d '\r')
    for candidate in "$home/bin/keytool" "$home/bin/keytool.exe"; do
        if [ -n "$home" ] && [ -x "$candidate" ]; then
            echo "$candidate"
            return
        fi
    done
}
keytool=$(find_keytool || true)

# --- Signing ------------------------------------------------------------------------------------
keystore_file="liftbook-release.jks"
if [ ! -f keystore.properties ]; then
    echo "No keystore.properties yet; creating a signing key."
    if [ -z "$keytool" ]; then
        echo "keytool not found (it comes with the JDK)." >&2
        exit 1
    fi
    password="${LIFTBOOK_KEYSTORE_PASSWORD:-}"
    if [ -z "$password" ]; then
        if [ ! -t 0 ]; then
            echo "No terminal to ask for a password. Set LIFTBOOK_KEYSTORE_PASSWORD, or write keystore.properties by hand (README)." >&2
            exit 1
        fi
        while :; do
            read -rs -p "Choose a keystore password (6+ characters): " password
            echo
            read -rs -p "Type it again: " again
            echo
            if [ "$password" != "$again" ]; then
                echo "They don't match; try again."
                continue
            fi
            if [ "${#password}" -lt 6 ]; then
                echo "Six characters at least."
                continue
            fi
            break
        done
    fi
    if [ ! -f "$keystore_file" ]; then
        "$keytool" -genkeypair -keystore "$keystore_file" -alias liftbook -keyalg RSA -keysize 2048 -validity 10000 \
            -storepass "$password" -keypass "$password" -dname "CN=LiftBook, OU=Personal, O=LiftBook"
    fi
    # app/build.gradle.kts resolves storeFile against the app module, hence the "../".
    umask 077
    cat > keystore.properties <<PROPERTIES
storeFile=../$keystore_file
storePassword=$password
keyAlias=liftbook
keyPassword=$password
PROPERTIES
    echo "Created $keystore_file and keystore.properties. Back both up: every update must be signed with this key."
fi

# --- Build --------------------------------------------------------------------------------------
./gradlew --quiet assembleRelease
apk="app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$apk" ]; then
    echo "The build produced no signed APK (looked for $apk). Check keystore.properties." >&2
    exit 1
fi
version=$(sed -nE 's/.*versionName = "([^"]+)".*/\1/p' app/build.gradle.kts | head -1)
mkdir -p dist
out="dist/liftbook-${version:-release}-$(date +%Y%m%d-%H%M).apk"
cp "$apk" "$out"
echo "Release APK: $root/$out ($(du -h "$out" | cut -f1))"

# --- Install (optional) -------------------------------------------------------------------------
if $install; then
    adb_bin=$(command -v adb || true)
    if [ -z "$adb_bin" ]; then
        sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
        if [ -z "$sdk" ] && [ -f local.properties ]; then
            # local.properties escapes ":" and "\" the Java way.
            sdk=$(sed -nE 's/^sdk\.dir=(.*)$/\1/p' local.properties | sed 's/\\\\/\//g; s/\\:/:/g')
        fi
        [ -z "$sdk" ] && sdk="$HOME/Android/Sdk"
        adb_bin="$sdk/platform-tools/adb"
    fi
    if [ ! -x "$adb_bin" ]; then
        echo "adb not found; install $out by hand." >&2
        exit 1
    fi
    "$adb_bin" install -r "$out"
    echo "Installed on the connected device."
fi
