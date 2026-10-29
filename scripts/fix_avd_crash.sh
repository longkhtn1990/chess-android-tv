#!/usr/bin/env bash
set -euo pipefail

AVD_DIR="$HOME/.android/avd/Xiaomi_Mi_Box_S_4K.avd"
CONFIG="$AVD_DIR/config.ini"

# Xóa các file lock bị treo do qemu crash
rm -f "$AVD_DIR"/*.lock 2>/dev/null || true
rm -rf "$AVD_DIR/snapshots" 2>/dev/null || true

# Tăng RAM & Heap, bật GPU cứng ổn định, buộc Cold Boot tránh snapshot lỗi
python3 -c "
with open('$CONFIG', 'r') as f:
    lines = f.readlines()

new_lines = []
skip_keys = {'vm.heapSize', 'hw.gpu.enabled', 'hw.gpu.mode', 'fastboot.forceColdBoot', 'fastboot.forceFastBoot', 'firstboot.bootFromLocalSnapshot', 'hw.ramSize'}
for line in lines:
    key = line.split('=')[0].strip()
    if key not in skip_keys:
        new_lines.append(line)

new_lines.append('vm.heapSize=512M\n')
new_lines.append('hw.ramSize=3G\n')
new_lines.append('hw.gpu.enabled=yes\n')
new_lines.append('hw.gpu.mode=host\n')
new_lines.append('fastboot.forceColdBoot=yes\n')
new_lines.append('fastboot.forceFastBoot=no\n')
new_lines.append('firstboot.bootFromLocalSnapshot=no\n')

with open('$CONFIG', 'w') as f:
    f.writelines(new_lines)
print('Config updated successfully')
"
