#!/usr/bin/env bash
set -e

export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH
export ANDROID_SERIAL=emulator-5554

BACKUP_DIR="/Users/harrylong/Documents/IDEA/Android-tivi/BanSaoLuu-TV-20261023-2235"
ARTIFACT_DIR="/Users/harrylong/.gemini/antigravity/brain/4c051695-3d62-4145-b5b1-55fc21880b00"
PKG="com.gathin.tvhome"
URI="content://$PKG.theme"

echo "1. Xóa thư mục tạo bởi shell (uid 2000) để ứng dụng CleanYourTV tự khởi tạo đúng quyền trên Android 14..."
adb shell "rm -rf /sdcard/Android/data/$PKG"

echo "2. Khởi chạy CleanYourTV để tạo thư mục external files thuộc quyền sở hữu của app..."
adb shell am start -W -n $PKG/.MainActivity >/dev/null
sleep 2

echo "3. Nạp config.json và 23 ảnh nền qua ThemeProvider ($URI)..."
adb push "$BACKUP_DIR/theme/config.json" /data/local/tmp/cyt-config.json >/dev/null
adb shell "content write --uri $URI/config.json < /data/local/tmp/cyt-config.json; rm -f /data/local/tmp/cyt-config.json"

for IMAGE in "$BACKUP_DIR"/theme/wallpapers/*.jpg; do
    [ -f "$IMAGE" ] || continue
    NAME=$(basename "$IMAGE")
    adb push "$IMAGE" "/data/local/tmp/cyt-$NAME" >/dev/null
    adb shell "content write --uri $URI/wallpapers/$NAME < /data/local/tmp/cyt-$NAME; rm -f /data/local/tmp/cyt-$NAME"
done

REV=$(uuidgen | tr -d '-')
adb shell "printf '%s' '$REV' > /data/local/tmp/cyt-rev; content write --uri $URI/theme_revision < /data/local/tmp/cyt-rev; rm -f /data/local/tmp/cyt-rev"

echo "4. Khởi động lại CleanYourTV Home và di chuyển khung chọn vào icon Cờ Vua TV 4K..."
adb shell am force-stop $PKG
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n $PKG/.MainActivity >/dev/null
sleep 3
adb shell input keyevent KEYCODE_DPAD_RIGHT
sleep 1

echo "5. Chụp ảnh màn hình CleanYourTV Home với ảnh nền Album và icon Cờ Vua TV 4K..."
adb shell screencap -p /sdcard/screen0.png
adb pull /sdcard/screen0.png "${ARTIFACT_DIR}/tv_chess_0_launcher_home.png"

echo "6. Mở lại Cờ Vua TV 4K để sẵn sàng trên máy ảo..."
adb shell input keyevent KEYCODE_DPAD_CENTER
sleep 2
