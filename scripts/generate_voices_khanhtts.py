#!/usr/bin/env python3
"""
Kịch bản sinh bộ 49 câu thoại tương tác cho game "ĐẬU HỌC CỜ VUA"
bằng kho mã nguồn `vuhai2002/vietnamese-tts-studio` (mô hình `kjanh/KhanhTTS-OmniVoice` 24kHz).

Đặc điểm:
- Sử dụng tăng tốc phần cứng GPU Apple Silicon (MPS) / CUDA.
- Tự động khởi tạo hoặc nạp giọng mẫu chuẩn (`refs/dau_female_warm.wav`) và khóa đồng nhất
  giọng nói qua `VoiceClonePrompt` cho toàn bộ 49 file thoại trong game.
- Hậu kỳ âm thanh chuẩn truyền hình EBU R128 (-16 LUFS, 44.1kHz AAC `.m4a`) qua FFmpeg.
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

import numpy as np
import soundfile as sf
import torch

SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_DIR = SCRIPT_DIR.parent
STUDIO_DIR = PROJECT_DIR / "tools" / "vietnamese-tts-studio"
RAW_DIR = PROJECT_DIR / "app" / "src" / "main" / "res" / "raw"
REFS_DIR = STUDIO_DIR / "refs"
TMP_WAV_DIR = STUDIO_DIR / "outputs" / "dau_game_wavs"

# Đưa thư mục vietnamese-tts-studio vào sys.path TRƯỚC khi import tts_engine
# để HF_HOME trỏ đúng vào tools/vietnamese-tts-studio/.cache/huggingface
sys.path.insert(0, str(STUDIO_DIR))

import tts_engine  # noqa: E402
import vietnamese_number_normalizer  # noqa: E402

MODEL_ID = "kjanh/KhanhTTS-OmniVoice"
REF_WAV_PATH = REFS_DIR / "dau_female_warm.wav"
REF_TEXT = "Chào bé Đậu dễ thương! Hôm nay mình cùng học cờ vua thiệt vui nha!"
REF_INSTRUCT = "female, young adult, moderate pitch"

VOICE_LINES: list[tuple[str, str]] = [
    # 1. Chào hỏi & Đối thủ (6 file)
    ("voice_welcome_dau_1", "Chào bé Đậu dễ thương! Hôm nay mình cùng học cờ vua thiệt vui nha!"),
    ("voice_welcome_dau_2", "Đậu ơi! Bàn cờ sẵn sàng rồi nè, Đậu chọn một quân lính để xuất phát nghen!"),
    ("voice_bot_dino", "Xin chào bé Đậu! Mình là Khủng Long Nhí vui vẻ nè! Đậu chơi cờ với mình nha!"),
    ("voice_bot_kingkong", "Chào bé Đậu nha! Mình là King Kong Con khỏe mạnh đây! Đậu cố gắng thắng mình nghen!"),
    ("voice_bot_godzilla", "Chào Đậu ơi! Mình là Rồng Nhí siêu cấp nè! Bé Đậu đã sẵn sàng thi tài chưa ta?"),
    ("voice_new_game_dau", "Ván cờ mới bắt đầu rồi! Chúc bé Đậu của mình đánh thắng thiệt giòn giã nha!"),

    # 2. Dạy Đậu chọn từng quân cờ - mỗi quân 2 biến thể (12 file)
    ("voice_select_pawn", "Đậu chọn Lính Tốt dũng cảm nè! Lính Tốt đi thẳng về phía trước từng bước nha Đậu!"),
    ("voice_select_pawn_2", "Lính Tốt nhỏ mà có võ đó Đậu ơi! Tiến lên cuối bàn cờ là được biến hình luôn nha!"),
    ("voice_select_knight", "Ngựa Chiến của Đậu xuất kích! Ngựa nhảy hình chữ L qua đầu mấy quân khác được luôn đó Đậu!"),
    ("voice_select_knight_2", "Đậu chọn Ngựa Chiến thông minh nè! Nhảy chữ L thiệt khéo để bắt quân đối phương nha!"),
    ("voice_select_bishop", "Quân Tượng thông thái nè Đậu ơi! Tượng trượt đường chéo thiệt nhanh khắp bàn cờ nha!"),
    ("voice_select_bishop_2", "Đậu chọn Quân Tượng rồi! Mình nhắm đường chéo nào trống để trượt tới nghen Đậu!"),
    ("voice_select_rook", "Xe Pháo Đài vững chắc của Đậu đây! Xe chạy thẳng ngang dọc mạnh mẽ lắm nha!"),
    ("voice_select_rook_2", "Oa, Xe Pháo Đài xuất trận kìa Đậu ơi! Đi ngang hay đi dọc đều siêu mạnh luôn!"),
    ("voice_select_queen", "Đậu chọn Nữ Hoàng quyền năng luôn! Nữ Hoàng đi ngang, đi dọc hay đi chéo gì cũng được hết trơn!"),
    ("voice_select_queen_2", "Nữ Hoàng mạnh nhất bàn cờ của Đậu ra tay rồi! Đậu chọn ô đi thiệt chuẩn nha!"),
    ("voice_select_king", "Đức Vua tối cao của Đậu nè! Đậu nhớ bảo vệ Vua cẩn thận và đi từng bước một thôi nha!"),
    ("voice_select_king_2", "Đức Vua đi chậm mà chắc nha Đậu ơi! Đừng để quân đối phương tới gần Vua nghen!"),

    # 3. Khen Đậu khi đi nước cờ thường (5 file)
    ("voice_player_move_1", "Nước đi hay quá Đậu ơi! Đợi mình gãi đầu suy nghĩ chút xíu nha!"),
    ("voice_player_move_2", "Chà, hôm nay Đậu đánh cờ khéo ghê ta! Tới lượt mình tính nước đi nè!"),
    ("voice_player_move_3", "Đậu thông minh quá chừng luôn! Để mình xem coi nên đi quân nào tiếp đây ta!"),
    ("voice_player_move_4", "Hay lắm Đậu nha! Nước cờ này làm mình phải suy nghĩ kỹ mới được!"),
    ("voice_player_move_5", "Giỏi quá Đậu ơi! Tới lượt mình đi rồi, Đậu quan sát bàn cờ nha!"),

    # 4. Hào hứng khi Đậu ăn quân của máy (4 file)
    ("voice_player_capture_1", "Ối! Đậu bắt mất quân của mình rồi! Đậu đánh giỏi quá trời luôn!"),
    ("voice_player_capture_2", "Oa! Hoan hô bé Đậu! Một pha bắt quân siêu đỉnh luôn nha!"),
    ("voice_player_capture_3", "Hu hu, mất tiêu một quân lính của mình rồi! Đậu tinh mắt thiệt đó nha!"),
    ("voice_player_capture_4", "Quá xuất sắc luôn Đậu ơi! Đậu vừa thu phục thêm một quân cờ nữa kìa!"),

    # 5. Động viên khi máy ăn quân của Đậu (3 file)
    ("voice_bot_capture", "Hì hì, mình bắt được một quân rồi! Hông sao đâu Đậu ơi, Đậu cố lên nha!"),
    ("voice_bot_capture_2", "Đậu mất một lính rồi, nhưng Đậu đừng buồn nha, mình tìm cách bắt lại nghen!"),
    ("voice_bot_capture_3", "Cố lên Đậu ơi! Quan sát kỹ bàn cờ hoặc bấm nút Mách nước để mình phụ Đậu nha!"),

    # 6. Chiếu tướng, Nhập thành, Phong cấp, Mách nước & Nhắc khi Đậu nghỉ tay lâu (8 file)
    ("voice_check", "Ái chà, cẩn thận nha Đậu ơi! Chiếu tướng rồi kìa!"),
    ("voice_check_player", "Trời ơi, Đậu chiếu tướng mình rồi! Đậu đánh hay quá vậy nè!"),
    ("voice_check_bot", "Ủa, Đức Vua đang bị chiếu tướng kìa Đậu ơi! Đậu mau cứu Vua nha!"),
    ("voice_castling", "Tuyệt vời! Đậu vừa đi nước Nhập thành, bảo vệ Đức Vua an toàn trong pháo đài luôn!"),
    ("voice_promotion", "Quá giỏi luôn Đậu ơi! Lính Tốt tới cuối bàn cờ rồi, Đậu chọn biến hình thành Nữ Hoàng nha!"),
    ("voice_hint_active", "Mình mách nước cho Đậu rồi đó! Đậu hãy đi quân cờ đang phát sáng màu xanh nha!"),
    ("voice_idle_encourage_1", "Đậu ơi, Đậu đang suy nghĩ nước đi hả? Nếu khó quá thì bấm nút bóng đèn để mình mách nước cho nha!"),
    ("voice_idle_encourage_2", "Tới lượt bé Đậu đi cờ đó! Đậu thử chọn một quân cờ có vòng sáng màu vàng xem sao nghen!"),

    # 7. Kết thúc ván cờ (4 file)
    ("voice_victory_player", "Hoan hô! Bé Đậu chiến thắng tuyệt vời luôn! Mình xin bái phục Đậu đó nha!"),
    ("voice_victory_player_2", "Xuất sắc quá Đậu ơi! Đậu học cờ vua giỏi số một luôn, thưởng cho Đậu thiệt nhiều ngôi sao nè!"),
    ("voice_victory_bot", "Ván này mình thắng mất rồi! Đậu đánh hay lắm, mình chơi lại ván nữa để Đậu phục thù nha!"),
    ("voice_draw", "Ván cờ hòa rồi Đậu ơi! Hai tụi mình tài ngang sức ngang tài luôn nha!"),

    # 8. Đọc tên các nút Menu & Tay cầm cho Đậu (7 file)
    ("voice_menu_resume", "Tiếp tục chơi cờ nha Đậu!"),
    ("voice_menu_undo", "Đi lại nước cờ vừa rồi!"),
    ("voice_menu_hint", "Bóng đèn mách nước đi hay cho Đậu!"),
    ("voice_menu_new_game", "Bắt đầu ván cờ mới nha!"),
    ("voice_menu_color", "Đổi màu quân cờ của Đậu!"),
    ("voice_menu_sound", "Bật tắt âm thanh!"),
    ("voice_menu_exit", "Thoát ra màn hình ti vi!"),
]


def find_ffmpeg() -> str:
    candidates = ["/opt/homebrew/bin/ffmpeg", shutil.which("ffmpeg") or ""]
    for c in candidates:
        if c and os.path.isfile(c) and os.access(c, os.X_OK):
            return c
    raise RuntimeError("Không tìm thấy công cụ ffmpeg trên hệ thống!")


def find_ffprobe() -> str:
    candidates = ["/opt/homebrew/bin/ffprobe", shutil.which("ffprobe") or ""]
    for c in candidates:
        if c and os.path.isfile(c) and os.access(c, os.X_OK):
            return c
    raise RuntimeError("Không tìm thấy công cụ ffprobe trên hệ thống!")


RESOURCE_NAME_PATTERN = re.compile(r"^[a-z][a-z0-9_]*$")


def validate_resource_key(key: str) -> None:
    if not isinstance(key, str) or not RESOURCE_NAME_PATTERN.match(key):
        raise ValueError(
            f"Tên tài nguyên không hợp lệ: '{key}'. "
            f"Android resource name chỉ được chứa chữ thường (a-z), chữ số (0-9) và dấu gạch dưới (_), "
            f"bắt đầu bằng một chữ cái thường."
        )


def load_voice_lines_from_json(json_path: Path) -> list[tuple[str, str]]:
    if not json_path.exists():
        raise FileNotFoundError(f"Không tìm thấy file JSON: {json_path}")
    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, list):
        raise ValueError(f"Dữ liệu JSON trong {json_path} phải là danh sách [{{'key': ..., 'text': ...}}]")

    lines: list[tuple[str, str]] = []
    invalid_keys: list[str] = []
    for idx, item in enumerate(data):
        if not isinstance(item, dict):
            raise ValueError(f"Phần tử thứ {idx} không phải dictionary: {item}")
        key = item.get("key")
        text = item.get("text")
        if not key or not isinstance(key, str) or not RESOURCE_NAME_PATTERN.match(key):
            invalid_keys.append(f"#{idx}: '{key}'")
            continue
        if not text or not isinstance(text, str):
            raise ValueError(f"Khóa '{key}' thiếu nội dung text hợp lệ!")
        lines.append((key, text))

    if invalid_keys:
        err_msg = (
            "Phát hiện các khóa tài nguyên không hợp lệ trong file JSON (không khớp ^[a-z][a-z0-9_]*$):\n"
            + "\n".join(f"  - {k}" for k in invalid_keys)
        )
        raise ValueError(err_msg)

    return lines


def normalize_text_line(raw_text: str) -> str:
    """Tách khoảng trắng giữa chữ cái và số (ví dụ: e4 -> e 4, a1 -> a 1)
    để bộ chuẩn hóa số đọc rõ từng từ, tránh dính âm như 'ebốn'.
    """
    spaced = re.sub(r"([a-zA-Z])(\d+)", r"\1 \2", raw_text)
    return vietnamese_number_normalizer.normalize(spaced)


def convert_wav_to_m4a(ffmpeg_bin: str, wav_path: Path, m4a_path: Path) -> None:
    """Chuyển WAV 24kHz từ KhanhTTS-OmniVoice sang M4A 44.1kHz chuẩn hóa âm lượng EBU R128."""
    af_chain = (
        "aresample=44100,"
        "volume=0.92,"
        "highpass=f=85,"
        "equalizer=f=300:t=q:w=1.2:g=1.2,"
        "equalizer=f=2800:t=q:w=1.4:g=1.2,"
        "lowpass=f=12000,"
        "loudnorm=I=-16:TP=-1.5:LRA=10"
    )
    subprocess.run(
        [
            ffmpeg_bin,
            "-y",
            "-v",
            "error",
            "-i",
            str(wav_path),
            "-af",
            af_chain,
            "-c:a",
            "aac",
            "-b:a",
            "112k",
            "-ar",
            "44100",
            str(m4a_path),
        ],
        check=True,
    )


def probe_audio(ffprobe_bin: str, file_path: Path) -> dict:
    cmd = [
        ffprobe_bin,
        "-v",
        "error",
        "-select_streams",
        "a:0",
        "-show_entries",
        "stream=sample_rate,channels,codec_name,duration:format=duration,size",
        "-of",
        "json",
        str(file_path),
    ]
    res = subprocess.run(cmd, capture_output=True, text=True, check=True)
    data = json.loads(res.stdout)
    stream = data.get("streams", [{}])[0]
    format_info = data.get("format", {})
    duration = float(stream.get("duration") or format_info.get("duration", 0.0))
    sample_rate = int(stream.get("sample_rate", 0))
    channels = int(stream.get("channels", 0))
    codec = stream.get("codec_name", "")
    size = int(format_info.get("size", 0))
    return {
        "duration": duration,
        "sample_rate": sample_rate,
        "channels": channels,
        "codec": codec,
        "size": size,
    }


def measure_loudness(ffmpeg_bin: str, file_path: Path) -> dict:
    cmd = [
        ffmpeg_bin,
        "-i",
        str(file_path),
        "-af",
        "loudnorm=I=-16:TP=-1.5:LRA=10:print_format=json",
        "-f",
        "null",
        "-",
    ]
    res = subprocess.run(cmd, capture_output=True, text=True)
    stderr = res.stderr
    idx = stderr.rfind("{")
    if idx != -1:
        json_str = stderr[idx : stderr.rfind("}") + 1]
        try:
            return json.loads(json_str)
        except Exception:
            pass
    return {}


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Sinh câu thoại tiếng Việt cho Đậu Học Cờ Vua bằng KhanhTTS-OmniVoice"
    )
    parser.add_argument(
        "--lines-json",
        type=str,
        default=None,
        help="Đường dẫn file JSON chứa danh sách câu thoại [{'key': '...', 'text': '...'}]",
    )
    parser.add_argument(
        "--only-new",
        action="store_true",
        help="Chỉ sinh các file chưa tồn tại trong app/src/main/res/raw/, bỏ qua file đã có",
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=7,
        help="Kích thước batch khi sinh trên GPU (mặc định: 7)",
    )
    parser.add_argument(
        "--num-steps",
        type=int,
        default=16,
        help="Số bước diffusion inference (mặc định: 16)",
    )
    parser.add_argument(
        "--speed",
        type=float,
        default=1.0,
        help="Tốc độ đọc (mặc định: 1.0)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Kiểm tra hợp lệ dữ liệu và in kế hoạch sinh mà không nạp model",
    )
    args = parser.parse_args()

    ffmpeg_bin = find_ffmpeg()
    ffprobe_bin = find_ffprobe()
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    REFS_DIR.mkdir(parents=True, exist_ok=True)
    TMP_WAV_DIR.mkdir(parents=True, exist_ok=True)

    if args.lines_json:
        json_path = Path(args.lines_json).resolve()
        target_lines = load_voice_lines_from_json(json_path)
        source_desc = f"JSON: {json_path} ({len(target_lines)} câu)"
    else:
        for k, _ in VOICE_LINES:
            validate_resource_key(k)
        target_lines = list(VOICE_LINES)
        source_desc = f"VOICE_LINES mặc định ({len(target_lines)} câu)"

    # Lọc bỏ các file đã tồn tại nếu có cờ --only-new
    if args.only_new:
        initial_count = len(target_lines)
        target_lines = [
            item
            for item in target_lines
            if not ((RAW_DIR / f"{item[0]}.m4a").exists() and (RAW_DIR / f"{item[0]}.m4a").stat().st_size > 0)
        ]
        skipped_count = initial_count - len(target_lines)
        print(f"[i] Cờ --only-new: Đã bỏ qua {skipped_count} file đã tồn tại. Còn lại {len(target_lines)} file cần sinh.")

    if not target_lines:
        print("[✓] Không có câu thoại nào cần sinh mới (--only-new). Hoàn tất!")
        return

    if args.dry_run:
        print(f"[Dry-run] Sẽ sinh {len(target_lines)} file từ nguồn {source_desc}:")
        for k, t in target_lines:
            norm_t = normalize_text_line(t)
            print(f"  - {k}: \"{t}\" -> \"{norm_t}\"")
        return

    device, dtype = tts_engine.pick_device(force_cpu=False)
    print("=" * 72)
    print("SINH FILE THOẠI TIẾNG VIỆT CHO GAME BẰNG VIETNAMESE-TTS-STUDIO")
    print(f"Nguồn   : {source_desc}")
    print(f"Cần tạo : {len(target_lines)} file")
    print(f"Model   : {MODEL_ID}")
    print(f"Device  : {device} ({dtype})")
    print(f"FFmpeg  : {ffmpeg_bin}")
    print(f"FFprobe : {ffprobe_bin}")
    print(f"Output  : {RAW_DIR}")
    print("=" * 72)

    tts_engine.engine.ensure_loaded(device, dtype, MODEL_ID)
    model = tts_engine.engine.model

    # 1. Chuẩn bị file giọng mẫu chuẩn (nếu chưa có)
    if not REF_WAV_PATH.exists():
        candidate_existing = STUDIO_DIR / "outputs" / "ref_young_mod.wav"
        if candidate_existing.exists():
            shutil.copyfile(candidate_existing, REF_WAV_PATH)
            print(f"[i] Đã sao chép giọng mẫu chuẩn vào: {REF_WAV_PATH}")
        else:
            print(f"[i] Đang khởi tạo giọng mẫu chuẩn ({REF_INSTRUCT})...")
            torch.manual_seed(42)
            ref_wav = model.generate(
                text=REF_TEXT,
                language="Vietnamese",
                instruct=REF_INSTRUCT,
                num_step=24,
            )[0]
            sf.write(str(REF_WAV_PATH), np.asarray(ref_wav, dtype=np.float32), tts_engine.SAMPLE_RATE)
            print(f"[i] Đã lưu giọng mẫu chuẩn tại: {REF_WAV_PATH}")

    # 2. Tạo VoiceClonePrompt một lần duy nhất để khóa giọng đồng nhất 100%
    print("[i] Đang mã hóa VoiceClonePrompt từ giọng mẫu...")
    voice_prompt = model.create_voice_clone_prompt(
        ref_audio=str(REF_WAV_PATH),
        ref_text=REF_TEXT,
    )

    # 3. Sinh âm thanh theo lô
    batch_size = max(1, args.batch_size)
    total = len(target_lines)
    t_start = time.time()
    generated_count = 0

    for start_idx in range(0, total, batch_size):
        batch = target_lines[start_idx : start_idx + batch_size]
        names = [item[0] for item in batch]
        texts = [normalize_text_line(item[1]) for item in batch]

        b_t0 = time.time()
        print(
            f"\n[Batch {start_idx // batch_size + 1}/{(total + batch_size - 1) // batch_size}] "
            f"Đang sinh câu {start_idx + 1}..{start_idx + len(batch)}/{total}..."
        )
        torch.manual_seed(100 + start_idx)
        wavs = model.generate(
            text=texts,
            language="Vietnamese",
            voice_clone_prompt=voice_prompt,
            num_step=args.num_steps,
            speed=args.speed,
        )
        tts_engine.engine.empty_cache()

        for name, raw_text, wav in zip(names, [item[1] for item in batch], wavs):
            wav_arr = np.asarray(wav, dtype=np.float32)
            wav_path = TMP_WAV_DIR / f"{name}.wav"
            m4a_path = RAW_DIR / f"{name}.m4a"
            sf.write(str(wav_path), wav_arr, tts_engine.SAMPLE_RATE)
            convert_wav_to_m4a(ffmpeg_bin, wav_path, m4a_path)
            probe = probe_audio(ffprobe_bin, m4a_path)
            generated_count += 1
            print(
                f"  ✓ {name}.m4a ({probe['duration']:.2f}s, {probe['sample_rate']}Hz, "
                f"{probe['size'] / 1024:.1f} KB): \"{raw_text}\""
            )

        print(f"  -> Hoàn tất batch trong {time.time() - b_t0:.1f}s")

    total_elapsed = time.time() - t_start
    print("\n" + "=" * 72)
    print(f"HOÀN TẤT SINH {generated_count}/{total} FILE THOẠI TRONG {total_elapsed:.1f}s!")
    print(f"Thư mục tài nguyên game: {RAW_DIR}")
    print("=" * 72)


if __name__ == "__main__":
    main()
