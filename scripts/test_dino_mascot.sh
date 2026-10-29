#!/usr/bin/env bash
set -euo pipefail

ADB="/Users/harrylong/Library/Android/sdk/platform-tools/adb"
EMULATOR="/Users/harrylong/Library/Android/sdk/emulator/emulator"
APK="/Users/harrylong/Documents/IDEA/Android-tivi/app/build/outputs/apk/debug/app-debug.apk"
BACKUP_APK="/Users/harrylong/Documents/IDEA/Android-tivi/BanSaoLuu-TV-20261023-2235/app-cua-ban/com.harrylong.tvchess/base.apk"
ARTIFACT_DIR="/Users/harrylong/.gemini/antigravity/brain/4c051695-3d62-4145-b5b1-55fc21880b00"

cp -f "$APK" "$BACKUP_APK"

"$ADB" start-server
if ! "$ADB" devices | grep -q "emulator-"; then
    nohup "$EMULATOR" -avd Xiaomi_Mi_Box_S_4K -no-snapshot-load -gpu host >/tmp/tv_emu.log 2>&1 &
    "$ADB" wait-for-device
    while [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
        sleep 2
    done
    sleep 3
fi

export ANDROID_SERIAL="$("$ADB" devices | awk '/emulator-/{print $1; exit}')"
"$ADB" install -r "$APK"
"$ADB" shell pm clear com.harrylong.tvchess
"$ADB" shell am start -n com.harrylong.tvchess/.MainActivity
sleep 3.5

"$ADB" exec-out screencap -p > "$ARTIFACT_DIR/kids_chess_1_dino_happy.png"

# Bấm OK chọn quân Tốt để chú Khủng Long ngạc nhiên và đọc thoại
"$ADB" shell input keyevent 23
sleep 1.2
"$ADB" exec-out screencap -p > "$ARTIFACT_DIR/kids_chess_2_select_piece_surprised.png"

# Bấm OK đi quân Tốt để chú Khủng Long nghiêng đầu suy nghĩ
"$ADB" shell input keyevent 23
sleep 1.0
"$ADB" exec-out screencap -p > "$ARTIFACT_DIR/kids_chess_3_dino_thinking.png"

sleep 2.5

# Mở Menu Nút Hình Ảnh bằng nút BACK (4), di chuyển sang nút Đổi Đối Thủ (idx=3: Phải -> Xuống) rồi bấm OK
"$ADB" shell input keyevent 4
sleep 0.8
"$ADB" shell input keyevent 22
sleep 0.5
"$ADB" shell input keyevent 20
sleep 0.5
"$ADB" shell input keyevent 23
sleep 1.5
"$ADB" exec-out screencap -p > "$ARTIFACT_DIR/kids_chess_4_kingkong.png"

# Bấm OK thêm lần nữa để đổi sang Godzilla Nhí
"$ADB" shell input keyevent 23
sleep 1.5
"$ADB" exec-out screencap -p > "$ARTIFACT_DIR/kids_chess_5_godzilla.png"

echo "DONE ALL 3 MASCOTS VERIFICATION"
