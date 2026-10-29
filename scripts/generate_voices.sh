#!/usr/bin/env bash
set -e

# ==============================================================================
# Kịch bản tạo bộ 49 câu thoại tương tác ĐẬU HỌC CỜ VUA
# Sử dụng kho mã nguồn `vuhai2002/vietnamese-tts-studio` (KhanhTTS-OmniVoice 24kHz)
# Kết hợp khóa đồng nhất giọng mẫu (VoiceClonePrompt) và chuẩn hóa EBU R128 44.1kHz
# ==============================================================================

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STUDIO_DIR="${PROJECT_DIR}/tools/vietnamese-tts-studio"
VENV_PYTHON="${STUDIO_DIR}/.venv/bin/python"
GEN_SCRIPT="${PROJECT_DIR}/scripts/generate_voices_khanhtts.py"

if [ ! -x "$VENV_PYTHON" ]; then
    echo "[i] Đang khởi tạo môi trường Python 3.12 cho vietnamese-tts-studio..."
    mkdir -p "${PROJECT_DIR}/tools"
    if [ ! -d "$STUDIO_DIR" ]; then
        git clone https://github.com/vuhai2002/vietnamese-tts-studio.git "$STUDIO_DIR"
    fi
    (
        cd "$STUDIO_DIR"
        uv venv --python 3.12
        uv pip install torch torchaudio omnivoice soundfile fastapi "uvicorn[standard]" python-multipart
    )
fi

exec "$VENV_PYTHON" "$GEN_SCRIPT" "$@"
