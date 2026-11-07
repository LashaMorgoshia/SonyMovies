#!/bin/sh
set -eu
KADRI_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if [ -z "${JAVA_HOME:-}" ] && [ -d '/Applications/Android Studio.app/Contents/jbr/Contents/Home' ]; then
  JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
  export JAVA_HOME
fi
ANDROID_HOME=${ANDROID_HOME:-"$HOME/Library/Android/sdk"}
ANDROID_USER_HOME="$KADRI_ROOT/.android-home"
GRADLE_USER_HOME="$KADRI_ROOT/.gradle-home"
export ANDROID_HOME ANDROID_USER_HOME GRADLE_USER_HOME
cd "$KADRI_ROOT/android-tv"
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug
mkdir -p "$KADRI_ROOT/releases"
cp app/build/outputs/apk/debug/app-debug.apk "$KADRI_ROOT/releases/MovieNight-1.0.9.apk"
printf '\nAPK: %s\n' "$KADRI_ROOT/releases/MovieNight-1.0.9.apk"
