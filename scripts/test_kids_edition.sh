#!/usr/bin/env bash
set -e

export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home

ARTIFACT_DIR="/Users/harrylong/.gemini/antigravity/brain/4c051695-3d62-4145-b5b1-55fc21880b00"
APK_PATH="/Users/harrylong/Documents/IDEA/Android-tivi/app/build/outputs/apk/debug/app-debug.apk"
BACKUP_APK="/Users/harrylong/Documents/IDEA/Android-tivi/BanSaoLuu-TV-20261023-2235/app-cua-ban/com.harrylong.tvchess/base.apk"
AVD_NAME="Xiaomi_Mi_Box_S_4K"

cp "$APK_PATH" "$BACKUP_APK"

adb start-server
adb disconnect 127.0.0.1:5555 >/dev/null 2>&1 || true

if ! adb devices | grep -q "emulator-5554"; then
    echo "Khởi động emulator ${AVD_NAME}..."
    nohup emulator -avd "${AVD_NAME}" -no-snapshot-load -no-boot-anim -gpu auto > /tmp/emulator.log 2>&1 &
    sleep 5
fi

export ANDROID_SERIAL=emulator-5554
adb wait-for-device

for i in $(seq 1 60); do
    BOOT=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n' || true)
    if [ "$BOOT" = "1" ]; then
        break
    fi
    sleep 2
done

sleep 2
adb shell wm dismiss-keyguard || true
adb shell input keyevent KEYCODE_WAKEUP || true

echo "1. Cài đặt Cờ Vua TV 4K - Kids Edition lên máy ảo..."
adb install -r "$APK_PATH"
adb shell pm clear com.harrylong.tvchess >/dev/null 2>&1 || true

echo "2. Khởi chạy Cờ Vua TV 4K (Khủng Long Nhí chào bé)..."
adb shell am start -W -n com.harrylong.tvchess/.MainActivity
sleep 3
adb shell screencap -p /sdcard/kids1.png
adb pull /sdcard/kids1.png "${ARTIFACT_DIR}/kids_chess_1_dino_happy.png"

echo "3. Bấm OK chọn Lính Tốt (Khủng Long Nhí ngạc nhiên + phát thoại dạy cách đi)..."
adb shell input keyevent KEYCODE_DPAD_RIGHT
sleep 1
adb shell input keyevent KEYCODE_DPAD_CENTER
sleep 1
adb shell screencap -p /sdcard/kids2.png
adb pull /sdcard/kids2.png "${ARTIFACT_DIR}/kids_chess_2_select_piece_surprised.png"

echo "4. Bấm OK đi quân -> Chụp lúc Khủng Long Nhí gãi đầu suy nghĩ..."
adb shell input keyevent KEYCODE_DPAD_CENTER
sleep 1
adb shell screencap -p /sdcard/kids3.png
adb pull /sdcard/kids3.png "${ARTIFACT_DIR}/kids_chess_3_dino_thinking.png"

sleep 3
echo "5. Bấm BACK mở bảng Nút Hình Ảnh -> Chuyển sang King Kong Con..."
adb shell input keyevent KEYCODE_BACK
sleep 1
# Từ nút 0 (Chơi tiếp - hàng 0 cột 0), bấm DOWN (xuống hàng 1 cột 0 - Mách nước), bấm RIGHT (sang hàng 1 cột 1 - Đổi đối thủ: nút 3)
adb shell input keyevent KEYCODE_DPAD_DOWN
sleep 1
adb shell input keyevent KEYCODE_DPAD_RIGHT
sleep 1
# Bấm OK để đổi từ Khủng Long Nhí -> King Kong Con
adb shell input keyevent KEYCODE_DPAD_CENTER
sleep 1
adb shell screencap -p /sdcard/kids4.png
adb pull /sdcard/kids4.png "${ARTIFACT_DIR}/kids_chess_4_kingkong.png"

echo "6. Bấm OK lần nữa để đổi từ King Kong Con -> Godzilla Nhí..."
adb shell input keyevent KEYCODE_DPAD_CENTER
sleep 1
adb shell screencap -p /sdcard/kids5.png
adb pull /sdcard/kids5.png "${ARTIFACT_DIR}/kids_chess_5_godzilla.png"

ls -lh "${ARTIFACT_DIR}"/kids_chess_*.png
