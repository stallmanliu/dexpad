#!/usr/bin/env bash
# Build, sign, and install the portrait DeX touchpad.
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
APP="$REPO/dex-touchpad"
SDK="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
JAR="$SDK/platforms/android-36/android.jar"
BT="$SDK/build-tools/36.0.0"
NDK="$SDK/ndk/28.2.13676358"
CC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang"
BUILD="$APP/build"
KS="$BUILD/debug.keystore"

mkdir -p "$BUILD/classes"
rm -rf "$BUILD/classes" "$BUILD/compiled.zip" "$BUILD/base.apk" "$BUILD/raw.apk" "$BUILD/aligned.apk" "$BUILD/app.apk"
mkdir -p "$BUILD/classes"

echo "== native daemon"
"$CC" -O2 -Wall -Wextra -o "$BUILD/dextouchd" "$APP/native/dextouchd.c"

echo "== resources"
mkdir -p "$BUILD/assets"
cp "$BUILD/dextouchd" "$BUILD/assets/dextouchd"
"$BT/aapt2" compile -o "$BUILD/compiled.zip" --dir "$APP/res"
"$BT/aapt2" link \
  -I "$JAR" \
  --manifest "$APP/AndroidManifest.xml" \
  -o "$BUILD/base.apk" \
  --min-sdk-version 26 \
  --target-sdk-version 34 \
  -A "$BUILD/assets" \
  "$BUILD/compiled.zip"

echo "== java libraries"
LIBS="$APP/libs"
mkdir -p "$LIBS"
fetch_jar() {
  local dest="$1" url="$2"
  if [ -f "$dest" ]; then
    return
  fi
  echo "download $(basename "$dest")"
  local tmp
  tmp="$(mktemp)"
  curl -fsSL -o "$tmp" "$url"
  if unzip -l "$tmp" 2>/dev/null | grep -q 'classes.jar'; then
    local unpacked
    unpacked="$(mktemp -d)"
    unzip -qo "$tmp" classes.jar -d "$unpacked"
    mv "$unpacked/classes.jar" "$dest"
    rm -rf "$unpacked" "$tmp"
  else
    mv "$tmp" "$dest"
  fi
}
fetch_jar "$LIBS/libadb-android-bc-3.2.0.jar" \
  "https://jitpack.io/com/github/osservatorionessuno/libadb-android-bc/3.2.0/libadb-android-bc-3.2.0.aar"
BC_VER=1.78.1
BC_BASE="https://repo1.maven.org/maven2/org/bouncycastle"
for artifact in bcprov bcutil bcpkix bctls; do
  fetch_jar "$LIBS/${artifact}-jdk15to18-${BC_VER}.jar" \
    "$BC_BASE/${artifact}-jdk15to18/${BC_VER}/${artifact}-jdk15to18-${BC_VER}.jar"
done
CP="$JAR:$LIBS/libadb-android-bc-3.2.0.jar:$LIBS/bcprov-jdk15to18-1.78.1.jar:$LIBS/bcutil-jdk15to18-1.78.1.jar:$LIBS/bcpkix-jdk15to18-1.78.1.jar:$LIBS/bctls-jdk15to18-1.78.1.jar"
find "$APP/src/com/daniel/dextouch" -name '*.java' ! -path '*/inject/*' > "$BUILD/sources.txt"
javac --release 11 -classpath "$CP" -encoding UTF-8 -d "$BUILD/classes" @"$BUILD/sources.txt"
mapfile -t CLASSES < <(find "$BUILD/classes" -name '*.class')
"$BT/d8" --min-api 26 --lib "$JAR" --output "$BUILD" \
  "${CLASSES[@]}" \
  "$LIBS/libadb-android-bc-3.2.0.jar" \
  "$LIBS/bcprov-jdk15to18-1.78.1.jar" \
  "$LIBS/bcutil-jdk15to18-1.78.1.jar" \
  "$LIBS/bcpkix-jdk15to18-1.78.1.jar" \
  "$LIBS/bctls-jdk15to18-1.78.1.jar"

echo "== package"
cp "$BUILD/base.apk" "$BUILD/raw.apk"
(
  cd "$BUILD"
  shopt -s nullglob
  dex=(classes*.dex)
  zip -j -q raw.apk "${dex[@]}"
)
"$BT/zipalign" -f -p 4 "$BUILD/raw.apk" "$BUILD/aligned.apk"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass android -alias androiddebugkey \
    -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=DeX Pad,O=Local,C=CA"
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$BUILD/app.apk" "$BUILD/aligned.apk"
echo "APK $BUILD/app.apk"

if [ "${1:-}" = "--no-install" ]; then
  exit 0
fi
"$REPO/cs_gen/start-dextouch.sh" install
