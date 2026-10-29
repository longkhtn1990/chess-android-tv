#!/usr/bin/env bash
set -euo pipefail

ADB="/Users/harrylong/Library/Android/sdk/platform-tools/adb"
APK="/Users/harrylong/Documents/IDEA/Android-tivi/app/build/outputs/apk/debug/app-debug.apk"
SERIAL="emulator-5554"

echo "Đang đợi máy ảo hoàn tất nạp hệ điều hành Android TV..."
while [ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
    sleep 2
done

echo "Hệ điều hành đã sẵn sàng! Đang cài game..."
"$ADB" -s "$SERIAL" install -r "$APK"
"$ADB" -s "$SERIAL" shell pm clear com.harrylong.tvchess
"$ADB" -s "$SERIAL" shell am start -n com.harrylong.tvchess/.MainActivity
sleep 2

# Đưa cửa sổ Android Emulator lên mặt trước màn hình macOS
osascript -e 'tell application "System Events" to set frontmost of (first process whose name contains "qemu-system") to true' 2>/dev/null || true

# Chụp ảnh xác thực
"$ADB" -s "$SERIAL" exec-out screencap -p > /Users/harrylong/.gemini/antigravity/brain/4c051695-3d62-4145-b5b1-55fc21880b00/kids_chess_ready_to_play.png

echo "SẴN SÀNG CHƠI!"
