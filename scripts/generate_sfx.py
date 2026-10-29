#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Kịch bản tổng hợp bộ Âm thanh Gỗ Thật tự nhiên (Real Wood Acoustic SFX)
cho dự án "Đậu Học Cờ Vua" trên Android TV.

Sử dụng mô hình vật lý Modal Resonant Wood Physics:
- Hertzian non-linear compression impulse (tiếng nén va chạm gỗ đầm, chắc).
- Wood cavity modal resonance (cộng hưởng hốc bàn cờ và thớ gỗ mun/gỗ sồi).
- Damping phụ thuộc tần số theo cơ chế suy hao đàn hồi sợi xenlulo.
- Xuất định dạng Ogg Vorbis chuẩn game Android tối ưu cho SoundPool độ trễ 0ms.
"""

import os
import wave
import struct
import math
import random
import subprocess

SAMPLE_RATE = 44100
PROJECT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW_DIR = os.path.join(PROJECT_DIR, "app", "src", "main", "res", "raw")
TMP_DIR = "/tmp/sfx_wood_gen"

os.makedirs(RAW_DIR, exist_ok=True)
os.makedirs(TMP_DIR, exist_ok=True)

FFMPEG_BIN = "/opt/homebrew/bin/ffmpeg"
if not os.path.exists(FFMPEG_BIN):
    FFMPEG_BIN = "ffmpeg"


def save_wav_and_encode_m4a(name, samples):
    wav_path = os.path.join(TMP_DIR, f"{name}.wav")
    out_path = os.path.join(RAW_DIR, f"{name}.m4a")
    
    # Write 16-bit PCM WAV
    with wave.open(wav_path, "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(SAMPLE_RATE)
        clamped = [max(-32768, min(32767, int(s))) for s in samples]
        wf.writeframes(struct.pack(f"<{len(clamped)}h", *clamped))
        
    # Encode to AAC M4A via FFmpeg
    cmd = [
        FFMPEG_BIN, "-y", "-v", "error",
        "-i", wav_path,
        "-c:a", "aac", "-b:a", "128k",
        "-ar", "44100",
        out_path
    ]
    subprocess.run(cmd, check=True)
    os.remove(wav_path)
    print(f" -> Đã tạo SFX: {out_path} ({os.path.getsize(out_path)} bytes)")


# ------------------------------------------------------------------------------
# 1. Tiếng đặt quân cờ gỗ thật (Soft / Heavy)
# ------------------------------------------------------------------------------
def gen_wood_move(is_heavy=False):
    duration_s = 0.13 if is_heavy else 0.095
    num_samples = int(SAMPLE_RATE * duration_s)
    samples = []
    
    # Modes (freq, amp, damping)
    if is_heavy:
        # Hậu, Vua, Xe: âm trầm sâu, lực nén đầm của quân cờ gỗ to
        modes = [
            (128.0, 0.46, 26.0),
            (245.0, 0.36, 42.0),
            (510.0, 0.22, 70.0),
            (1020.0, 0.12, 115.0),
            (2100.0, 0.05, 180.0)
        ]
        transient_dur = 0.008
        transient_decay = 420.0
        pitch_drop = 0.09
    else:
        # Tốt, Mã, Tượng: âm cộc thanh thoát của gỗ sồi/gỗ gụ
        modes = [
            (178.0, 0.42, 36.0),
            (365.0, 0.35, 54.0),
            (730.0, 0.22, 88.0),
            (1460.0, 0.14, 140.0),
            (2800.0, 0.06, 220.0)
        ]
        transient_dur = 0.006
        transient_decay = 520.0
        pitch_drop = 0.05

    random.seed(101 if is_heavy else 202)
    for i in range(num_samples):
        t = i / SAMPLE_RATE
        
        # 1. Hertzian impact transient (dense compression knock)
        transient = 0.0
        if t < transient_dur:
            progress = t / transient_dur
            compression = math.sin(math.pi * progress) ** 1.8
            grain = (random.random() * 2.0 - 1.0) * 0.20
            transient = (compression + grain) * math.exp(-transient_decay * t) * 0.78
            
        # 2. Wood cavity modal resonance with natural pitch compression
        modal = 0.0
        p_bend = 1.0 + pitch_drop * math.exp(-140.0 * t)
        for freq, amp, damping in modes:
            env = math.exp(-damping * t)
            modal += math.sin(2.0 * math.pi * (freq * p_bend) * t) * amp * env
            
        val = transient * 0.55 + modal * 0.72
        
        # Smooth windowing
        if i < 20:
            val *= (i / 20.0)
        elif i > num_samples - 40:
            val *= ((num_samples - i) / 40.0)
            
        samples.append(val * 32767.0 * 0.90)
        
    name = "sfx_wood_move_heavy" if is_heavy else "sfx_wood_move_soft"
    save_wav_and_encode_m4a(name, samples)


# ------------------------------------------------------------------------------
# 2. Tiếng bắt quân cờ gỗ thật (Capture Impact kép + Star Chime ấm)
# ------------------------------------------------------------------------------
def gen_wood_capture():
    duration_s = 0.38
    num_samples = int(SAMPLE_RATE * duration_s)
    samples = []
    
    # 2 cú va đập gỗ liên tiếp (Double wood clack)
    hit1_modes = [(205.0, 0.40, 38.0), (430.0, 0.35, 60.0), (890.0, 0.22, 100.0)]
    hit2_modes = [(235.0, 0.38, 42.0), (480.0, 0.32, 65.0), (980.0, 0.20, 110.0)]
    hit2_delay = 0.018 # 18ms sau
    
    # Chuỗi 4 nốt chuông mộc vui tai thưởng bé Đậu: C5, E5, G5, C6
    chimes = [
        (0.045, 523.25, 0.28), # C5
        (0.105, 659.25, 0.26), # E5
        (0.165, 783.99, 0.25), # G5
        (0.225, 1046.50, 0.28) # C6
    ]
    
    random.seed(303)
    for i in range(num_samples):
        t = i / SAMPLE_RATE
        val = 0.0
        
        # Hit 1
        t1 = t
        env1 = math.exp(-380.0 * t1) if t1 < 0.008 else 0.0
        trans1 = (math.sin(math.pi * min(1.0, t1 / 0.007)) ** 1.8 + (random.random() * 2 - 1) * 0.15) * env1
        modal1 = sum(math.sin(2.0 * math.pi * f * t1) * a * math.exp(-d * t1) for f, a, d in hit1_modes)
        val += (trans1 * 0.50 + modal1 * 0.65)
        
        # Hit 2
        if t >= hit2_delay:
            t2 = t - hit2_delay
            env2 = math.exp(-420.0 * t2) if t2 < 0.008 else 0.0
            trans2 = (math.sin(math.pi * min(1.0, t2 / 0.006)) ** 1.8 + (random.random() * 2 - 1) * 0.15) * env2
            modal2 = sum(math.sin(2.0 * math.pi * f * t2) * a * math.exp(-d * t2) for f, a, d in hit2_modes)
            val += (trans2 * 0.45 + modal2 * 0.55) * 0.85
            
        # Chuông mộc ngân vang
        for st, f, amp in chimes:
            if t >= st:
                dt = t - st
                c_env = math.exp(-14.0 * dt)
                # Âm sắc mộc: sóng sin cơ bản + hài bậc 2 rất nhẹ
                c_tone = math.sin(2.0 * math.pi * f * dt) + 0.18 * math.sin(2.0 * math.pi * (f * 2.0) * dt)
                val += c_tone * c_env * amp
                
        # Fade edges
        if i < 20:
            val *= (i / 20.0)
        elif i > num_samples - 60:
            val *= ((num_samples - i) / 60.0)
            
        samples.append(val * 32767.0 * 0.85)
        
    save_wav_and_encode_m4a("sfx_wood_capture", samples)


# ------------------------------------------------------------------------------
# 3. Tiếng lướt quân cờ trên mặt gỗ (Slide Whoosh / Felt-on-Wood Friction)
# ------------------------------------------------------------------------------
def gen_wood_slide():
    duration_s = 0.125
    num_samples = int(SAMPLE_RATE * duration_s)
    samples = []
    
    random.seed(404)
    phase = 0.0
    for i in range(num_samples):
        t = i / SAMPLE_RATE
        prog = i / num_samples
        
        # Sweep mượt mà của âm dạ lướt
        freq = 320.0 + 220.0 * math.sin(math.pi * prog)
        phase += 2.0 * math.pi * freq / SAMPLE_RATE
        
        # Ma sát nỉ dạ mượt
        friction = (random.random() * 2.0 - 1.0) * 0.32
        body = math.sin(phase) * 0.68 + friction
        
        env = math.sin(math.pi * prog) ** 1.4
        val = body * env * 0.45
        
        # Cú chạm nhẹ ở cuối cú lướt
        if prog > 0.80:
            dt_tap = (prog - 0.80) * duration_s
            tap_env = math.exp(-80.0 * dt_tap)
            tap_val = math.sin(2.0 * math.pi * 180.0 * dt_tap) * tap_env * 0.25
            val += tap_val
            
        if i < 30:
            val *= (i / 30.0)
        elif i > num_samples - 40:
            val *= ((num_samples - i) / 40.0)
            
        samples.append(val * 32767.0 * 0.85)
        
    save_wav_and_encode_m4a("sfx_wood_slide", samples)


# ------------------------------------------------------------------------------
# 4. Tiếng gõ phím Marimba mộc dịu tai (Nav Marimba)
# ------------------------------------------------------------------------------
def gen_wood_nav():
    duration_s = 0.065
    num_samples = int(SAMPLE_RATE * duration_s)
    samples = []
    
    f0 = 660.0 # E5 mộc dịu
    f1 = 1980.0 # Hài bậc 3 đặc trưng Marimba undercut
    
    for i in range(num_samples):
        t = i / SAMPLE_RATE
        attack = min(1.0, t / 0.0018)
        decay = math.exp(-54.0 * t)
        env = attack * decay
        
        tone = 0.86 * math.sin(2.0 * math.pi * f0 * t) + 0.14 * math.sin(2.0 * math.pi * f1 * t) * math.exp(-85.0 * t)
        val = tone * env * 0.50
        
        if i < 15:
            val *= (i / 15.0)
        elif i > num_samples - 30:
            val *= ((num_samples - i) / 30.0)
            
        samples.append(val * 32767.0 * 0.85)
        
    save_wav_and_encode_m4a("sfx_wood_nav", samples)


# ------------------------------------------------------------------------------
# 5. Tiếng chọn quân cờ mộc tươi vui (Select Chime)
# ------------------------------------------------------------------------------
def gen_wood_select():
    duration_s = 0.16
    num_samples = int(SAMPLE_RATE * duration_s)
    samples = []
    
    # 2 nốt ấm áp C5 (523.25) -> G5 (783.99)
    n1_f = 523.25
    n2_f = 783.99
    n2_st = 0.050
    
    for i in range(num_samples):
        t = i / SAMPLE_RATE
        val = 0.0
        
        # Nốt 1
        e1 = math.exp(-28.0 * t)
        val += (math.sin(2.0 * math.pi * n1_f * t) + 0.18 * math.sin(2.0 * math.pi * (n1_f * 2.0) * t)) * e1 * 0.45
        
        # Nốt 2
        if t >= n2_st:
            dt = t - n2_st
            e2 = math.exp(-22.0 * dt)
            val += (math.sin(2.0 * math.pi * n2_f * dt) + 0.20 * math.sin(2.0 * math.pi * (n2_f * 2.0) * dt)) * e2 * 0.60
            
        if i < 20:
            val *= (i / 20.0)
        elif i > num_samples - 40:
            val *= ((num_samples - i) / 40.0)
            
        samples.append(val * 32767.0 * 0.80)
        
    save_wav_and_encode_m4a("sfx_wood_select", samples)


# ------------------------------------------------------------------------------
# 6. Tiếng cảnh báo chiếu tướng mộc du dương (Check Alert)
# ------------------------------------------------------------------------------
def gen_wood_check():
    duration_s = 0.29
    num_samples = int(SAMPLE_RATE * duration_s)
    samples = []
    
    chord2_st = 0.105
    
    for i in range(num_samples):
        t = i / SAMPLE_RATE
        val = 0.0
        
        # Pha 1: D5 (587.33) + F#5 (739.99)
        att1 = min(1.0, t / 0.012)
        e1 = att1 * math.exp(-14.0 * t)
        val += (math.sin(2.0 * math.pi * 587.33 * t) + 0.85 * math.sin(2.0 * math.pi * 739.99 * t)) * e1 * 0.42
        
        # Pha 2: A5 (880.0) + D6 (1174.66)
        if t >= chord2_st:
            dt = t - chord2_st
            att2 = min(1.0, dt / 0.010)
            e2 = att2 * math.exp(-11.0 * dt)
            val += (math.sin(2.0 * math.pi * 880.0 * dt) + 0.75 * math.sin(2.0 * math.pi * 1174.66 * dt)) * e2 * 0.52
            
        if i < 30:
            val *= (i / 30.0)
        elif i > num_samples - 50:
            val *= ((num_samples - i) / 50.0)
            
        samples.append(val * 32767.0 * 0.80)
        
    save_wav_and_encode_m4a("sfx_wood_check", samples)


# ------------------------------------------------------------------------------
# 7. Fanfare mộc chiến thắng & khích lệ (Game Over Win / Loss)
# ------------------------------------------------------------------------------
def gen_wood_fanfare(is_victory=True):
    duration_s = 0.68 if is_victory else 0.58
    num_samples = int(SAMPLE_RATE * duration_s)
    samples = []
    
    if is_victory:
        # C-Major Fanfare rộn rã: C5, E5, G5, C6
        notes = [(0.0, 523.25), (0.110, 659.25), (0.220, 783.99), (0.330, 1046.50)]
        for i in range(num_samples):
            t = i / SAMPLE_RATE
            val = 0.0
            for idx, (st, f) in enumerate(notes):
                if t >= st:
                    dt = t - st
                    att = min(1.0, dt / 0.008)
                    dec = 5.2 if idx == len(notes) - 1 else 11.0
                    env = att * math.exp(-dec * dt)
                    tone = math.sin(2.0 * math.pi * f * dt) + 0.22 * math.sin(2.0 * math.pi * (f * 2.0) * dt)
                    val += tone * env * (0.50 if idx == len(notes) - 1 else 0.36)
                    
            # Hòa âm thêm G5 và E6 ở đuôi
            if t >= 0.330:
                dt = t - 0.330
                c_env = min(1.0, dt / 0.015) * math.exp(-5.0 * dt)
                val += 0.18 * math.sin(2.0 * math.pi * 783.99 * dt) * c_env
                val += 0.14 * math.sin(2.0 * math.pi * 1318.51 * dt) * c_env
                
            if i < 40:
                val *= (i / 40.0)
            elif i > num_samples - 80:
                val *= ((num_samples - i) / 80.0)
                
            samples.append(val * 32767.0 * 0.85)
        name = "sfx_wood_fanfare_win"
    else:
        # Giai điệu mộc âu yếm: G5, E5, C5, D5
        notes = [(0.0, 783.99), (0.120, 659.25), (0.240, 523.25), (0.360, 587.33)]
        for i in range(num_samples):
            t = i / SAMPLE_RATE
            val = 0.0
            for idx, (st, f) in enumerate(notes):
                if t >= st:
                    dt = t - st
                    att = min(1.0, dt / 0.012)
                    dec = 6.0 if idx == len(notes) - 1 else 10.0
                    env = att * math.exp(-dec * dt)
                    tone = math.sin(2.0 * math.pi * f * dt) + 0.15 * math.sin(2.0 * math.pi * (f * 2.0) * dt)
                    val += tone * env * 0.42
                    
            if i < 40:
                val *= (i / 40.0)
            elif i > num_samples - 80:
                val *= ((num_samples - i) / 80.0)
                
            samples.append(val * 32767.0 * 0.80)
        name = "sfx_wood_fanfare_loss"
        
    save_wav_and_encode_m4a(name, samples)


def main():
    print("======================================================================")
    print("BẮT ĐẦU TẠO 9 FILE SFX GỖ THẬT TỰ NHIÊN (AAC 44.1kHz M4A)")
    print("======================================================================")
    gen_wood_move(is_heavy=False)
    gen_wood_move(is_heavy=True)
    gen_wood_capture()
    gen_wood_slide()
    gen_wood_nav()
    gen_wood_select()
    gen_wood_check()
    gen_wood_fanfare(is_victory=True)
    gen_wood_fanfare(is_victory=False)
    print("======================================================================")
    print("HOÀN TẤT TẠO TOÀN BỘ 9 FILE SFX GỖ THẬT!")
    print("======================================================================")
    gen_wood_move(is_heavy=False)
    gen_wood_move(is_heavy=True)
    gen_wood_capture()
    gen_wood_slide()
    gen_wood_nav()
    gen_wood_select()
    gen_wood_check()
    gen_wood_fanfare(is_victory=True)
    gen_wood_fanfare(is_victory=False)
    print("======================================================================")
    print("HOÀN TẤT TẠO TOÀN BỘ 9 FILE SFX GỖ THẬT!")
    print("======================================================================")


if __name__ == "__main__":
    main()
