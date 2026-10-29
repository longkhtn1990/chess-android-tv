#!/usr/bin/env bash
set -euo pipefail

ADB="/Users/harrylong/Library/Android/sdk/platform-tools/adb"
EMULATOR="/Users/harrylong/Library/Android/sdk/emulator/emulator"
APK="/Users/harrylong/Documents/IDEA/Android-tivi/app/build/outputs/apk/debug/app-debug.apk"

# Dọn dẹp tiến trình emulator cũ nếu còn treo
pkill -9 -f "qemu-system" 2>/dev/null || true
pkill -9 -f "emulator.*Xiaomi_Mi_Box_S_4K" 2>/dev/null || true
sleep 1

# Xóa các file lock bị treo
rm -f "$HOME/.android/avd/Xiaomi_Mi_Box_S_4K.avd"/*.lock 2>/dev/null || true

"$ADB" start-server

echo "Khởi động máy ảo Xiaomi Mi Box S 4K (Cold Boot, ổn định cao)..."
nohup "$EMULATOR" -avd Xiaomi_Mi_Box_S_4K -no-snapshot -wipe-data -gpu host >/tmp/tv_emu.log 2>&1 &

echo "Đang đợi máy ảo khởi động..."
"$ADB" wait-for-device

while [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
    sleep 2
done

echo "Máy ảo đã khởi động xong! Đang cài đặt game..."
export ANDROID_SERIAL="$("$ADB" devices | awk '/emulator-/{print $1; exit}')"
"$ADB" install -r "$APK"
"$ADB" shell pm clear com.harrylong.tvchess
"$ADB" shell am start -n com.harrylong.tvchess/.MainActivity

# Đưa cửa sổ máy ảo lên phía trước
osascript -e 'tell application "System Events" to set frontmost of (first process whose name contains "qemu-system") to true' 2>/dev/null || true

echo "THÀNH CÔNG: Game đã mở và sẵn sàng chơi!"
