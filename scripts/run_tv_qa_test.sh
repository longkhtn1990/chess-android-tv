#!/usr/bin/env bash
set -e

echo "=== [1/7] Thiết lập biến môi trường Android ==="
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home

ARTIFACT_DIR="/Users/harrylong/.gemini/antigravity/brain/4c051695-3d62-4145-b5b1-55fc21880b00"
BACKUP_DIR="/Users/harrylong/Documents/IDEA/Android-tivi/BanSaoLuu-TV-20261023-2235"
APK_PATH="/Users/harrylong/Documents/IDEA/Android-tivi/app/build/outputs/apk/debug/app-debug.apk"
AVD_NAME="Xiaomi_Mi_Box_S_4K"

mkdir -p "$ARTIFACT_DIR"

echo "=== [2/7] Kết nối ADB Server & Máy ảo ==="
adb start-server
adb disconnect 127.0.0.1:5555 >/dev/null 2>&1 || true

if ! adb devices | grep -q "emulator-5554"; then
    echo "Khởi động emulator ${AVD_NAME}..."
    nohup emulator -avd "${AVD_NAME}" -no-snapshot-load -no-boot-anim -gpu auto > /tmp/emulator.log 2>&1 &
    sleep 5
fi

export ANDROID_SERIAL=emulator-5554
echo "Đang chờ thiết bị kết nối ADB ($ANDROID_SERIAL)..."
adb wait-for-device

echo "Đang đợi hệ điều hành Android 14 hoàn tất khởi động (sys.boot_completed)..."
for i in $(seq 1 60); do
    BOOT=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n' || true)
    if [ "$BOOT" = "1" ]; then
        echo "Android 14 đã khởi động xong!"
        break
    fi
    sleep 2
done

sleep 2
adb shell wm dismiss-keyguard || true
adb shell input keyevent KEYCODE_WAKEUP || true

echo "=== [3/7] Cài đặt Bản Sao Lưu TV (BanSaoLuu-TV-20261023-2235) lên Máy ảo ==="
# 3.1 Cài các ứng dụng trong app-cua-ban
for dir in "$BACKUP_DIR"/app-cua-ban/*/; do
    [ -d "$dir" ] || continue
    pkg=$(basename "$dir")
    if adb shell pm list packages | tr -d '\r' | grep -qx "package:$pkg"; then
        echo " - Ứng dụng $pkg: đã được cài đặt."
    else
        echo " - Đang cài đặt $pkg từ bản sao lưu..."
        apks=("$dir"*.apk)
        if [ "${#apks[@]}" -eq 1 ]; then
            adb install -r "${apks[0]}" || echo "   (Bỏ qua $pkg nếu không tương thích ABI)"
        else
            adb install-multiple -r "${apks[@]}" || echo "   (Bỏ qua $pkg nếu không tương thích ABI)"
        fi
    fi
done

# 3.2 Cài đặt Launcher CleanYourTV từ bản sao lưu
LAUNCHER_APK="$BACKUP_DIR/launcher/app/build/outputs/apk/release/app-release.apk"
echo " - Đang cài đặt Launcher CleanYourTV (com.gathin.tvhome)..."
adb install -r "$LAUNCHER_APK"
adb shell pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION 2>/dev/null || true
adb shell pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS 2>/dev/null || true
adb shell cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity || true
adb shell settings put secure enabled_accessibility_services "com.gathin.tvhome/.InputKeyService:com.gathin.tvhome/.HomeKeyService" || true
adb shell settings put secure accessibility_enabled 1 || true

# 3.3 Nạp Album Ảnh Nền & Cấu hình Theme (10s/ảnh, dock 7 ô) từ bản sao lưu
echo " - Đang nạp Theme & Album 23 ảnh nền từ BanSaoLuu-TV-20261023-2235/theme..."
PKG="com.gathin.tvhome"
URI="content://$PKG.theme"
adb shell "rm -rf /sdcard/Android/data/$PKG"
adb shell am start -W -n $PKG/.MainActivity >/dev/null
sleep 2

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

# Đặt Screensaver Album ảnh như trên TV thật
adb shell settings put secure screensaver_components com.gathin.tvhome/.PhotoDream || true
adb shell settings put secure screensaver_enabled 1 || true
adb shell settings put secure screensaver_activate_on_sleep 1 || true

echo "=== [4/7] Cài đặt Game Cờ Vua TV 4K (com.harrylong.tvchess) ==="
adb install -r "$APK_PATH"

echo "=== [5/7] Kiểm tra Màn hình chính CleanYourTV (từ Bản Sao Lưu) ==="
adb shell am force-stop com.gathin.tvhome || true
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n com.gathin.tvhome/.MainActivity
sleep 4
echo "Chụp ảnh màn hình 0: Màn hình chính CleanYourTV sau khi khôi phục bản sao lưu (tv_chess_0_launcher_home.png)"
adb shell screencap -p /sdcard/screen0.png
adb pull /sdcard/screen0.png "${ARTIFACT_DIR}/tv_chess_0_launcher_home.png"

echo "=== [6/7] Mở Game Cờ Vua TV 4K & Giả lập Remote Xiaomi ==="
adb shell am start -W -n com.harrylong.tvchess/.MainActivity
sleep 4

echo "Chụp ảnh màn hình 1: Giao diện Bàn cờ ban đầu (tv_chess_1_initial.png)"
adb shell screencap -p /sdcard/screen1.png
adb pull /sdcard/screen1.png "${ARTIFACT_DIR}/tv_chess_1_initial.png"

echo "- Nhấn DPAD_RIGHT để nhảy con trỏ sang quân bên phải..."
adb shell input keyevent KEYCODE_DPAD_RIGHT
sleep 1

echo "- Nhấn DPAD_CENTER (OK) để chọn quân cờ..."
adb shell input keyevent KEYCODE_DPAD_CENTER
sleep 1

echo "Chụp ảnh màn hình 2: Quân cờ được chọn & chấm xanh nước đi hợp lệ (tv_chess_2_selected_piece.png)"
adb shell screencap -p /sdcard/screen2.png
adb pull /sdcard/screen2.png "${ARTIFACT_DIR}/tv_chess_2_selected_piece.png"

echo "- Nhấn DPAD_CENTER (OK) để xác nhận đánh quân cờ tới ô đích..."
adb shell input keyevent KEYCODE_DPAD_CENTER
echo "Đợi 4s để AI suy nghĩ và phản hồi nước đi của Đen..."
sleep 4

echo "Chụp ảnh màn hình 3: Sau khi AI đáp trả (tv_chess_3_after_ai_move.png)"
adb shell screencap -p /sdcard/screen3.png
adb pull /sdcard/screen3.png "${ARTIFACT_DIR}/tv_chess_3_after_ai_move.png"

echo "- Nhấn BACK trên Remote Xiaomi để mở Menu Điều Khiển bên phải..."
adb shell input keyevent KEYCODE_BACK
sleep 1

echo "Chụp ảnh màn hình 4: Menu Điều Khiển mở ra (tv_chess_4_side_menu.png)"
adb shell screencap -p /sdcard/screen4.png
adb pull /sdcard/screen4.png "${ARTIFACT_DIR}/tv_chess_4_side_menu.png"

echo "=== [7/7] HOÀN TẤT CÀI ĐẶT BẢN SAO LƯU & THỬ NGHIỆM GAME CỜ VUA ==="
ls -lh "${ARTIFACT_DIR}"/tv_chess_*.png
