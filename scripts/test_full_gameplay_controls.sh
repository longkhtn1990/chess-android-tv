#!/usr/bin/env bash
set -euo pipefail

ADB="/opt/homebrew/share/android-commandlinetools/platform-tools/adb"
export PATH="/opt/homebrew/share/android-commandlinetools/platform-tools:/opt/homebrew/opt/openjdk@17/bin:$PATH"

echo "========================================================"
echo "    BẮT ĐẦU KIỂM THỬ TỰ ĐỘNG TOÀN DIỆN ĐẬU HỌC CỜ VUA"
echo "========================================================"

# Xóa logcat cũ và khởi động app
$ADB logcat -c
$ADB shell pm clear com.harrylong.tvchess
$ADB shell am start -n com.harrylong.tvchess/.MainActivity
sleep 2.5

echo "--> Bước 1: Kiểm tra màn hình khởi động & chào bé Đậu"
$ADB logcat -d | grep -iE "TVCHESS|Gamepad" || true

echo "--> Bước 2: Bé bấm Nút A (KEYCODE_BUTTON_A = 96) chọn Tốt e2"
$ADB shell input keyevent 96
sleep 0.8
$ADB logcat -d | grep "TVCHESS_GAMEPLAY" | tail -n 5

echo "--> Bước 3: Bé bấm tiếp Nút A (96) để đi nước e4 ngay lập tức!"
$ADB shell input keyevent 96
sleep 2.5
$ADB logcat -d | grep "TVCHESS_GAMEPLAY" | tail -n 5

echo "--> Bước 4: Kiểm tra phản hồi của Bot sau khi bé đi e4"
$ADB logcat -d | grep -iE "TVCHESS_GAMEPLAY|bestmove" | tail -n 10

echo "--> Bước 5: Bé thử nút Undo Y (KEYCODE_BUTTON_Y = 100)"
$ADB shell input keyevent 100
sleep 1.0
$ADB logcat -d | grep -iE "TVCHESS" | tail -n 5

echo "--> Bước 6: Bé thử chuyển đổi 3D/2D bằng nút SELECT (KEYCODE_BUTTON_SELECT = 109)"
$ADB shell input keyevent 109
sleep 1.0

echo "--> Bước 7: Bé thử nút Mách Nước X (KEYCODE_BUTTON_X = 99)"
$ADB shell input keyevent 99
sleep 1.5

echo "--> Bước 8: Bé thử nút Đổi quân kế tiếp RB (KEYCODE_BUTTON_R1 = 103)"
$ADB shell input keyevent 103
sleep 0.8

echo "--> Bước 9: Bé chọn quân Mã và thực hiện nước đi Mã"
$ADB shell input keyevent 96 # Chọn quân
sleep 0.8
$ADB shell input keyevent 96 # Xác nhận đi ô mục tiêu tự động
sleep 2.5

echo "--> Bước 10: Chụp ảnh màn hình bàn cờ sau loạt kiểm thử"
$ADB shell screencap -p /sdcard/screen_after_full_test.png
$ADB pull /sdcard/screen_after_full_test.png /tmp/screen_after_full_test.png

echo "========================================================"
echo "    HOÀN TẤT KIỂM THỬ TOÀN BỘ DI CHUYỂN VÀ ĐIỀU KHIỂN!"
echo "========================================================"
