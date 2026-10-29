#!/usr/bin/env bash
set -euo pipefail

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export PATH="$JAVA_HOME/bin:/opt/homebrew/share/android-commandlinetools/platform-tools:/Users/harrylong/Library/Android/sdk/platform-tools:$PATH"

cd /Users/harrylong/Documents/IDEA/Android-tivi
./gradlew assembleDebug

ADB="$(which adb || echo "/opt/homebrew/share/android-commandlinetools/platform-tools/adb")"
EMULATOR="/Users/harrylong/Library/Android/sdk/emulator/emulator"
APK="/Users/harrylong/Documents/IDEA/Android-tivi/app/build/outputs/apk/debug/app-debug.apk"
BACKUP_APK="/Users/harrylong/Documents/IDEA/Android-tivi/BanSaoLuu-TV-20261023-2235/app-cua-ban/com.harrylong.tvchess/base.apk"

cp -f "$APK" "$BACKUP_APK"
cp -f "$APK" /Users/harrylong/Documents/IDEA/Android-tivi/Dau-Hoc-Co-Vua-3D.apk
cp -f "$APK" /Users/harrylong/Documents/IDEA/Android-tivi/Dau-Hoc-Co-Vua.apk

"$ADB" start-server
if ! "$ADB" devices | grep -q "emulator-"; then
    nohup "$EMULATOR" -avd Xiaomi_Mi_Box_S_4K -no-snapshot-load -gpu host >/tmp/tv_emu.log 2>&1 &
    "$ADB" wait-for-device
    while [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
        sleep 2
    done
    sleep 2
fi

export ANDROID_SERIAL="$("$ADB" devices | awk '/emulator-/{print $1; exit}')"
"$ADB" install -r "$APK"
"$ADB" shell pm clear com.harrylong.tvchess
"$ADB" shell am start -n com.harrylong.tvchess/.MainActivity

# Đưa cửa sổ máy ảo Android TV lên phía trước màn hình macOS để người dùng điều khiển ngay
osascript -e 'tell application "System Events" to set frontmost of (first process whose name contains "qemu-system") to true' 2>/dev/null || true

echo "GAME STARTED AND READY TO PLAY"
