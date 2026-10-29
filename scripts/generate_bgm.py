#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Kịch bản tổng hợp Nhạc Nền Nhẹ Nhàng & Thư Giãn (Procedural Relaxing BGM)
cho trò chơi "Đậu Học Cờ Vua" trên Android TV & Điện thoại Android.

5 Bản nhạc nền hoàn chỉnh (Seamless Loops 60–75s):
1. bgm_home: Vui tươi, ấm áp (Kalimba + Piano + Pad + Bass, C Major, 80 BPM, 66.0s)
2. bgm_lesson: Êm dịu, tập trung (Music Box + Ambient Pad + Soft Piano, F Major, 72 BPM, 66.7s)
3. bgm_dino: Thư thái, dễ thương (Kalimba + Glockenspiel + Pizzicato, G Major, 76 BPM, 63.2s)
4. bgm_kingkong: Nhẹ nhàng, sôi nổi (Marimba + Kalimba + Rhythmic Bass, D Major, 82 BPM, 70.2s)
5. bgm_godzilla: Trầm, hơi hồi hộp nhưng êm (Low Piano + Music Box + Deep Pad, D Dorian, 74 BPM, 71.4s)

Hậu kỳ FFmpeg:
- Tần số 44.100 Hz, chuẩn âm thanh truyền hình EBU R128 (-23 LUFS, True Peak <= -2 dBTP)
- Định dạng AAC tối ưu game Android (~84-88 kbps), tổng dung lượng < 4 MB
"""

import os
import sys
import json
import math
import wave
import random
import subprocess
import numpy as np

SAMPLE_RATE = 44100
PROJECT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW_DIR = os.path.join(PROJECT_DIR, "app", "src", "main", "res", "raw")
TMP_DIR = "/tmp/bgm_gen"

os.makedirs(RAW_DIR, exist_ok=True)
os.makedirs(TMP_DIR, exist_ok=True)

FFMPEG_BIN = "/opt/homebrew/bin/ffmpeg"
if not os.path.exists(FFMPEG_BIN):
    FFMPEG_BIN = "ffmpeg"


# ------------------------------------------------------------------------------
# 1. Hệ thống nốt nhạc & thang âm (Pitch & Scale Utilities)
# ------------------------------------------------------------------------------
NOTE_MAP = {
    'C': 0, 'C#': 1, 'DB': 1,
    'D': 2, 'D#': 3, 'EB': 3,
    'E': 4,
    'F': 5, 'F#': 6, 'GB': 6,
    'G': 7, 'G#': 8, 'AB': 8,
    'A': 9, 'A#': 10, 'BB': 10,
    'B': 11
}

def note_freq(name_or_val):
    """Chuyển đổi tên nốt (vd 'C4', 'F#3', 'Bb5') hoặc MIDI sang tần số Hz."""
    if isinstance(name_or_val, (int, float)):
        return 440.0 * (2.0 ** ((name_or_val - 69.0) / 12.0))
    s = str(name_or_val).strip().upper()
    if len(s) == 3:
        note_letter = s[:2]
        octave = int(s[2])
    else:
        note_letter = s[0]
        octave = int(s[1])
    semitone = NOTE_MAP[note_letter]
    midi_num = (octave + 1) * 12 + semitone
    return 440.0 * (2.0 ** ((midi_num - 69.0) / 12.0))


# ------------------------------------------------------------------------------
# 2. Bộ Tổng Hợp Âm Sắc Vật Lý / Mộc (Physical / Acoustic Timbre Synthesis)
# ------------------------------------------------------------------------------

def synth_kalimba(freq, dur_s, velocity=0.8):
    """
    Đàn Kalimba châu Phi (Thumb Piano):
    - Chùm dao động lưỡi thép: mode cơ bản f0, mode 2 (2.756 f0), mode 3 (5.404 f0)
    - Tiếng gõ móng tay / búng ngón (chiff click) đặc trưng
    """
    n = int(SAMPLE_RATE * dur_s)
    t = np.linspace(0, dur_s, n, endpoint=False)
    
    # Modes
    m1 = np.sin(2 * np.pi * freq * t) * np.exp(-3.2 * t)
    m2 = 0.32 * np.sin(2 * np.pi * freq * 2.756 * t) * np.exp(-16.0 * t)
    m3 = 0.10 * np.sin(2 * np.pi * freq * 5.404 * t) * np.exp(-38.0 * t)
    
    # Click transient
    n_click = min(n, int(SAMPLE_RATE * 0.003))
    if n_click > 0:
        click = np.sin(2 * np.pi * 1850 * t[:n_click]) * np.exp(-1200 * t[:n_click]) * 0.18
        sig = m1 + m2 + m3
        sig[:n_click] += click
    else:
        sig = m1 + m2 + m3
        
    return np.tanh(1.2 * sig) * 0.85 * velocity


def synth_music_box(freq, dur_s, velocity=0.8):
    """
    Hộp nhạc cơ học (Music Box / Celesta):
    - Âm thanh răng lược kim loại trong vắt, ngân vang ngọt ngào
    - Hài âm cao dạng chuông chime (4.2 f0)
    """
    n = int(SAMPLE_RATE * dur_s)
    t = np.linspace(0, dur_s, n, endpoint=False)
    
    m1 = np.sin(2 * np.pi * freq * t) * np.exp(-2.2 * t)
    m2 = 0.28 * np.sin(2 * np.pi * (freq * 2.0) * t) * np.exp(-4.2 * t)
    m3 = 0.08 * np.sin(2 * np.pi * (freq * 3.0) * t) * np.exp(-7.5 * t)
    m4 = 0.14 * np.sin(2 * np.pi * (freq * 4.22) * t) * np.exp(-13.0 * t)
    
    n_click = min(n, int(SAMPLE_RATE * 0.002))
    if n_click > 0:
        click = np.sin(2 * np.pi * 3100 * t[:n_click]) * np.exp(-1800 * t[:n_click]) * 0.12
        sig = m1 + m2 + m3 + m4
        sig[:n_click] += click
    else:
        sig = m1 + m2 + m3 + m4
        
    return sig * 0.78 * velocity


def synth_soft_piano(freq, dur_s, velocity=0.8):
    """
    Đàn Piano gõ bọc nỉ mềm (Felt Piano):
    - Dây kép detune nhẹ (chorus acoustic tự nhiên)
    - Hài âm mềm mại, suy hao theo tần số
    """
    n = int(SAMPLE_RATE * dur_s)
    t = np.linspace(0, dur_s, n, endpoint=False)
    
    detune = 0.38
    f1 = freq - detune
    f2 = freq + detune
    env = np.exp(-2.0 * t)
    
    h1 = 0.52 * (np.sin(2 * np.pi * f1 * t) + np.sin(2 * np.pi * f2 * t))
    h2 = 0.24 * np.sin(2 * np.pi * (freq * 2) * t) * np.exp(-3.4 * t)
    h3 = 0.11 * np.sin(2 * np.pi * (freq * 3) * t) * np.exp(-5.2 * t)
    h4 = 0.04 * np.sin(2 * np.pi * (freq * 4) * t) * np.exp(-7.5 * t)
    
    n_thump = min(n, int(SAMPLE_RATE * 0.006))
    if n_thump > 0:
        thump = np.sin(2 * np.pi * 115 * t[:n_thump]) * np.exp(-450 * t[:n_thump]) * 0.10
        sig = (h1 + h2 + h3 + h4) * env
        sig[:n_thump] += thump
    else:
        sig = (h1 + h2 + h3 + h4) * env
        
    return np.tanh(1.1 * sig) * 0.82 * velocity


def synth_marimba(freq, dur_s, velocity=0.8):
    """
    Đàn Marimba thanh gỗ gõ ấm (Wooden Marimba):
    - Hài âm bậc 3 undercut đặc trưng của phím gỗ mộc
    """
    n = int(SAMPLE_RATE * dur_s)
    t = np.linspace(0, dur_s, n, endpoint=False)
    
    env = np.exp(-8.5 * t)
    m1 = np.sin(2 * np.pi * freq * t)
    m2 = 0.22 * np.sin(2 * np.pi * (freq * 3.0) * t) * np.exp(-22.0 * t)
    
    n_click = min(n, int(SAMPLE_RATE * 0.0025))
    if n_click > 0:
        click = np.sin(2 * np.pi * 1550 * t[:n_click]) * np.exp(-1400 * t[:n_click]) * 0.16
        sig = (m1 + m2) * env
        sig[:n_click] += click
    else:
        sig = (m1 + m2) * env
        
    return sig * 0.76 * velocity


def synth_warm_pad(freqs, dur_s, velocity=0.6):
    """
    Đệm Ambient Pad ấm áp, êm đềm như mây lững lờ:
    - Attack chậm 0.7s, release 0.9s
    - Hợp âm đa âm sắc với vibrato siêu chậm 0.22 Hz
    """
    n = int(SAMPLE_RATE * dur_s)
    t = np.linspace(0, dur_s, n, endpoint=False)
    
    att_n = int(SAMPLE_RATE * 0.65)
    rel_n = int(SAMPLE_RATE * 0.85)
    env = np.ones(n, dtype=np.float32)
    if att_n > 0:
        env[:att_n] = 0.5 * (1.0 - np.cos(np.pi * np.linspace(0, 1, att_n)))
    if rel_n > 0 and rel_n < n:
        env[-rel_n:] = 0.5 * (1.0 + np.cos(np.pi * np.linspace(0, 1, rel_n)))
        
    chord = np.zeros(n, dtype=np.float32)
    for f in freqs:
        vibrato = 0.0016 * np.sin(2 * np.pi * 0.22 * t)
        f_t = f * (1.0 + vibrato)
        phase = 2 * np.pi * np.cumsum(f_t) / SAMPLE_RATE
        chord += np.sin(phase) + 0.30 * np.sin(2 * phase) + 0.08 * np.sin(3 * phase)
        
    chord /= max(1, len(freqs))
    return chord * env * 0.44 * velocity


def synth_acoustic_bass(freq, dur_s, velocity=0.75):
    """
    Bass mộc ấm (Upright / Acoustic Wood Bass):
    - Nốt trầm tròn trịa, giữ nhịp êm tai cho bé suy nghĩ
    """
    n = int(SAMPLE_RATE * dur_s)
    t = np.linspace(0, dur_s, n, endpoint=False)
    env = np.exp(-2.2 * t)
    
    b1 = np.sin(2 * np.pi * freq * t)
    b2 = 0.38 * np.sin(2 * np.pi * (freq * 2) * t) * np.exp(-3.8 * t)
    b3 = 0.09 * np.sin(2 * np.pi * (freq * 3) * t) * np.exp(-5.5 * t)
    
    n_pluck = min(n, int(SAMPLE_RATE * 0.005))
    if n_pluck > 0:
        pluck = (np.random.rand(n_pluck) * 2 - 1) * np.exp(-280 * t[:n_pluck]) * 0.06
        sig = (b1 + b2 + b3) * env
        sig[:n_pluck] += pluck
    else:
        sig = (b1 + b2 + b3) * env
        
    return np.tanh(1.15 * sig) * 0.72 * velocity


# ------------------------------------------------------------------------------
# 3. Bộ Không Gian Reverb & Khử Tiếng "Click" Vòng Lặp Liền Mạch (Seamless Loop)
# ------------------------------------------------------------------------------

def apply_stereo_reverb(stereo_audio, wet=0.22, dry=0.88):
    """
    Tạo không gian phòng nhạc ấm áp bằng tích chập FFT phản xạ phòng:
    - Phản xạ sớm lệch pha Stereo mở rộng trường âm
    - Đuôi Reverb suy hao tự nhiên theo vật lý âm học
    """
    ir_dur = 1.35
    ir_len = int(SAMPLE_RATE * ir_dur)
    t = np.linspace(0, ir_dur, ir_len, endpoint=False)
    
    # Khởi tạo đáp ứng xung (Impulse Response)
    np.random.seed(12345)
    ir_l = np.random.randn(ir_len).astype(np.float32) * np.exp(-t / 0.38)
    ir_r = np.random.randn(ir_len).astype(np.float32) * np.exp(-t / 0.38)
    
    # Lọc dịu tần số cao (High-frequency damping)
    w = 7
    ir_l = np.convolve(ir_l, np.ones(w)/w, mode='same')
    ir_r = np.convolve(ir_r, np.ones(w)/w, mode='same')
    
    # Chuẩn hóa năng lượng IR
    ir_l /= np.sqrt(np.sum(ir_l ** 2) + 1e-8)
    ir_r /= np.sqrt(np.sum(ir_r ** 2) + 1e-8)
    
    # FFT Convolution nhanh
    n_total = len(stereo_audio) + ir_len - 1
    out_l = np.fft.irfft(np.fft.rfft(stereo_audio[:, 0], n=n_total) * np.fft.rfft(ir_l, n=n_total), n=n_total)[:len(stereo_audio)]
    out_r = np.fft.irfft(np.fft.rfft(stereo_audio[:, 1], n=n_total) * np.fft.rfft(ir_r, n=n_total), n=n_total)[:len(stereo_audio)]
    
    result = np.column_stack((
        stereo_audio[:, 0] * dry + out_l * wet,
        stereo_audio[:, 1] * dry + out_r * wet
    ))
    return result


def make_seamless_loop(audio, loop_len, tail_len, micro_fade_len=240):
    """
    Kỹ thuật Seamless Loop chuyên nghiệp:
    1. Cộng đuôi vang Reverb và note release từ sau điểm kết thúc (T_loop) trở lại điểm bắt đầu (T=0)
    2. Micro-fade 5.4ms (240 samples) triệt tiêu hoàn toàn bất kỳ bước nhảy DC nào tại điểm nối vòng lặp
    """
    loop = np.copy(audio[:loop_len])
    tail = audio[loop_len : loop_len + tail_len]
    actual_tail = min(len(tail), loop_len)
    loop[:actual_tail] += tail[:actual_tail]
    
    # Micro-fade cosine mượt mà tại biên
    W = micro_fade_len
    fade_in = 0.5 * (1.0 - np.cos(np.pi * np.linspace(0, 1, W)[:, None]))
    fade_out = 0.5 * (1.0 + np.cos(np.pi * np.linspace(0, 1, W)[:, None]))
    
    loop[:W] *= fade_in
    loop[-W:] *= fade_out
    return loop


# ------------------------------------------------------------------------------
# 4. Trình Soạn Nhạc Thủ Tục (Procedural Score Composer)
# ------------------------------------------------------------------------------

class ScoreComposer:
    def __init__(self, bpm, num_bars, beats_per_bar=4, tail_bars=1):
        self.bpm = bpm
        self.beats_per_bar = beats_per_bar
        self.num_bars = num_bars
        self.sec_per_beat = 60.0 / bpm
        self.sec_per_bar = self.sec_per_beat * beats_per_bar
        
        self.loop_duration = self.num_bars * self.sec_per_bar
        self.tail_duration = tail_bars * self.sec_per_bar
        self.total_duration = self.loop_duration + self.tail_duration
        
        self.total_samples = int(SAMPLE_RATE * self.total_duration)
        self.loop_samples = int(SAMPLE_RATE * self.loop_duration)
        self.tail_samples = self.total_samples - self.loop_samples
        
        self.buffer = np.zeros((self.total_samples, 2), dtype=np.float32)
        random.seed(int(bpm * 100 + num_bars))

    def beat_to_time(self, bar, beat):
        """Tính thời gian bắt đầu (giây) theo chỉ số bar (0-indexed) và beat (0-indexed)."""
        return (bar * self.beats_per_bar + beat) * self.sec_per_beat

    def add_note(self, inst_name, bar, beat, pitch, dur_beats=1.5, vel=0.8, pan=0.0):
        """Thêm nốt đơn với humanization nhẹ về thời gian và lực nhấn."""
        t_base = self.beat_to_time(bar, beat)
        # Humanize: lệch thời gian +/- 4ms, vận tốc +/- 5%
        t_jitter = (random.random() * 2.0 - 1.0) * 0.004
        t_start = max(0.0, t_base + t_jitter)
        v_jitter = (random.random() * 2.0 - 1.0) * 0.05
        actual_vel = max(0.1, min(1.0, vel + v_jitter))
        
        dur_s = dur_beats * self.sec_per_beat
        freq = note_freq(pitch)
        
        if inst_name == 'kalimba':
            sig = synth_kalimba(freq, dur_s, actual_vel)
        elif inst_name == 'music_box':
            sig = synth_music_box(freq, dur_s, actual_vel)
        elif inst_name == 'piano':
            sig = synth_soft_piano(freq, dur_s, actual_vel)
        elif inst_name == 'marimba':
            sig = synth_marimba(freq, dur_s, actual_vel)
        elif inst_name == 'bass':
            sig = synth_acoustic_bass(freq, dur_s, actual_vel)
        else:
            sig = synth_kalimba(freq, dur_s, actual_vel)
            
        start_samp = int(SAMPLE_RATE * t_start)
        end_samp = min(self.total_samples, start_samp + len(sig))
        n_add = end_samp - start_samp
        if n_add <= 0:
            return
            
        # Equal-power panning
        pan_l = math.cos((pan + 1.0) * math.pi / 4.0)
        pan_r = math.sin((pan + 1.0) * math.pi / 4.0)
        
        self.buffer[start_samp:end_samp, 0] += sig[:n_add] * pan_l
        self.buffer[start_samp:end_samp, 1] += sig[:n_add] * pan_r

    def add_pad(self, bar, beat, pitches, dur_beats=4.0, vel=0.6, pan=0.0):
        """Thêm hợp âm Ambient Pad êm ái."""
        t_start = self.beat_to_time(bar, beat)
        dur_s = dur_beats * self.sec_per_beat
        freqs = [note_freq(p) for p in pitches]
        sig = synth_warm_pad(freqs, dur_s, vel)
        
        start_samp = int(SAMPLE_RATE * t_start)
        end_samp = min(self.total_samples, start_samp + len(sig))
        n_add = end_samp - start_samp
        if n_add <= 0:
            return
            
        pan_l = math.cos((pan + 1.0) * math.pi / 4.0)
        pan_r = math.sin((pan + 1.0) * math.pi / 4.0)
        
        self.buffer[start_samp:end_samp, 0] += sig[:n_add] * pan_l
        self.buffer[start_samp:end_samp, 1] += sig[:n_add] * pan_r

    def build(self, wet_reverb=0.22):
        """Áp dụng Reverb không gian và tạo vòng lặp mượt mà không click."""
        reverbed = apply_stereo_reverb(self.buffer, wet=wet_reverb, dry=0.88)
        loop = make_seamless_loop(reverbed, self.loop_samples, self.tail_samples)
        return loop, self.loop_duration


# ------------------------------------------------------------------------------
# 5. Soạn Thảo 5 Bản Nhạc Cụ Thể (The 5 Unique Chess Themes)
# ------------------------------------------------------------------------------

def compose_bgm_home():
    """
    1. bgm_home: Vui tươi, ấm áp (Trang chủ & Menu chọn chế độ)
    Key: C Major | Tempo: 80 BPM | 22 Bars (66.0s)
    Instruments: Kalimba lead + Piano arpeggio + Warm pad + Acoustic bass
    """
    c = ScoreComposer(bpm=80, num_bars=22, beats_per_bar=4, tail_bars=1)
    
    # 22 bars chord sequence (I - V - vi - IV - I - iii - IV - V...)
    chords = [
        # Phrase 1: Bars 0-3 (C -> G -> Am7 -> Fmaj7)
        (['C3', 'E3', 'G3'], ['C4', 'E4', 'G4'], 'C2'),
        (['B2', 'D3', 'G3'], ['B3', 'D4', 'G4'], 'G2'),
        (['A2', 'C3', 'E3'], ['C4', 'E4', 'A4'], 'A2'),
        (['F2', 'A2', 'C3'], ['A3', 'C4', 'F4'], 'F2'),
        # Phrase 2: Bars 4-7 (C/E -> Em7 -> F -> Gsus4)
        (['E3', 'G3', 'C4'], ['G3', 'C4', 'E4'], 'E2'),
        (['E3', 'G3', 'B3'], ['G3', 'B3', 'E4'], 'E2'),
        (['F2', 'A2', 'C3'], ['A3', 'C4', 'F4'], 'F2'),
        (['G2', 'C3', 'D3'], ['G3', 'C4', 'D4'], 'G2'),
        # Phrase 3: Bars 8-11 (Am7 -> Em7 -> Fmaj7 -> C)
        (['A2', 'C3', 'E3'], ['C4', 'E4', 'A4'], 'A2'),
        (['E3', 'G3', 'B3'], ['G3', 'B3', 'E4'], 'E2'),
        (['F2', 'A2', 'C3'], ['A3', 'C4', 'F4'], 'F2'),
        (['C3', 'E3', 'G3'], ['E3', 'G3', 'C4'], 'C2'),
        # Phrase 4: Bars 12-15 (Dm7 -> G7 -> Em7 -> Am7)
        (['D3', 'F3', 'A3'], ['F3', 'A3', 'D4'], 'D2'),
        (['G2', 'B2', 'F3'], ['F3', 'G3', 'B3'], 'G2'),
        (['E3', 'G3', 'B3'], ['G3', 'B3', 'E4'], 'E2'),
        (['A2', 'C3', 'E3'], ['C4', 'E4', 'A4'], 'A2'),
        # Phrase 5: Bars 16-19 (Fmaj7 -> G -> C -> Cmaj7)
        (['F2', 'A2', 'C3'], ['A3', 'C4', 'F4'], 'F2'),
        (['G2', 'B2', 'D3'], ['B3', 'D4', 'G4'], 'G2'),
        (['C3', 'E3', 'G3'], ['E4', 'G4', 'C5'], 'C2'),
        (['C3', 'E3', 'B3'], ['E4', 'B4', 'C5'], 'C2'),
        # Phrase 6 (Turnaround): Bars 20-21 (Fmaj7 -> G7 leading back to C)
        (['F2', 'A2', 'C3'], ['A3', 'C4', 'F4'], 'F2'),
        (['G2', 'B2', 'F3'], ['B3', 'D4', 'F4'], 'G2')
    ]
    
    # Kalimba melody line notes (joyful, welcoming for bé Đậu)
    kalimba_melody = [
        # Bar 0 (C): G4, E5, D5, C5
        (0, 0.0, 'G4', 1.0, 0.85), (0, 1.0, 'E5', 1.0, 0.90), (0, 2.0, 'D5', 0.8, 0.82), (0, 3.0, 'C5', 1.2, 0.85),
        # Bar 1 (G): D5, G4, A4, B4
        (1, 0.0, 'D5', 1.2, 0.86), (1, 1.5, 'G4', 0.8, 0.78), (1, 2.5, 'A4', 0.8, 0.80), (1, 3.2, 'B4', 0.8, 0.82),
        # Bar 2 (Am): C5, E5, A5, G5
        (2, 0.0, 'C5', 1.0, 0.88), (2, 1.0, 'E5', 1.0, 0.92), (2, 2.0, 'A5', 1.2, 0.95), (2, 3.2, 'G5', 0.8, 0.82),
        # Bar 3 (F): E5, D5, C5, A4
        (3, 0.0, 'E5', 1.0, 0.85), (3, 1.2, 'D5', 0.8, 0.80), (3, 2.0, 'C5', 1.0, 0.82), (3, 3.0, 'A4', 1.0, 0.78),
        # Bar 4 (C): G4, C5, E5, G5
        (4, 0.0, 'G4', 0.8, 0.80), (4, 1.0, 'C5', 0.8, 0.84), (4, 2.0, 'E5', 1.0, 0.88), (4, 3.0, 'G5', 1.2, 0.92),
        # Bar 5 (Em): E5, B4, G4, E4
        (5, 0.0, 'E5', 1.2, 0.85), (5, 1.5, 'B4', 0.8, 0.80), (5, 2.5, 'G4', 0.8, 0.78), (5, 3.2, 'E4', 0.8, 0.75),
        # Bar 6 (F): A4, C5, F5, E5
        (6, 0.0, 'A4', 1.0, 0.82), (6, 1.0, 'C5', 1.0, 0.86), (6, 2.0, 'F5', 1.2, 0.92), (6, 3.2, 'E5', 0.8, 0.80),
        # Bar 7 (G): D5, G5, D5, B4
        (7, 0.0, 'D5', 1.0, 0.85), (7, 1.2, 'G5', 1.2, 0.95), (7, 2.5, 'D5', 0.8, 0.82), (7, 3.2, 'B4', 0.8, 0.78),
        # Bar 8 (Am): A4, C5, E5, D5
        (8, 0.0, 'A4', 1.0, 0.82), (8, 1.0, 'C5', 1.0, 0.85), (8, 2.0, 'E5', 1.0, 0.88), (8, 3.0, 'D5', 1.0, 0.82),
        # Bar 9 (Em): B4, G4, E4, G4
        (9, 0.0, 'B4', 1.2, 0.80), (9, 1.5, 'G4', 0.8, 0.76), (9, 2.5, 'E4', 0.8, 0.74), (9, 3.2, 'G4', 0.8, 0.78),
        # Bar 10 (F): A4, C5, E5, D5
        (10, 0.0, 'A4', 1.0, 0.82), (10, 1.0, 'C5', 1.0, 0.85), (10, 2.0, 'E5', 1.0, 0.88), (10, 3.0, 'D5', 1.0, 0.82),
        # Bar 11 (C): C5, G4, E4, C4
        (11, 0.0, 'C5', 1.5, 0.86), (11, 1.8, 'G4', 0.8, 0.78), (11, 2.6, 'E4', 0.8, 0.75), (11, 3.2, 'C4', 0.8, 0.72),
        # Bar 12 (Dm): D4, F4, A4, C5
        (12, 0.0, 'D4', 1.0, 0.80), (12, 1.0, 'F4', 1.0, 0.82), (12, 2.0, 'A4', 1.0, 0.86), (12, 3.0, 'C5', 1.2, 0.90),
        # Bar 13 (G): B4, D5, G5, F5
        (13, 0.0, 'B4', 1.0, 0.85), (13, 1.2, 'D5', 0.8, 0.82), (13, 2.0, 'G5', 1.2, 0.94), (13, 3.2, 'F5', 0.8, 0.80),
        # Bar 14 (Em): E5, B4, G4, E5
        (14, 0.0, 'E5', 1.2, 0.88), (14, 1.5, 'B4', 0.8, 0.80), (14, 2.5, 'G4', 0.8, 0.78), (14, 3.2, 'E5', 0.8, 0.84),
        # Bar 15 (Am): A5, E5, C5, A4
        (15, 0.0, 'A5', 1.4, 0.92), (15, 1.6, 'E5', 0.8, 0.84), (15, 2.4, 'C5', 0.8, 0.80), (15, 3.2, 'A4', 0.8, 0.76),
        # Bar 16 (F): C5, F5, A5, G5
        (16, 0.0, 'C5', 1.0, 0.85), (16, 1.0, 'F5', 1.0, 0.90), (16, 2.0, 'A5', 1.2, 0.95), (16, 3.2, 'G5', 0.8, 0.82),
        # Bar 17 (G): F5, E5, D5, B4
        (17, 0.0, 'F5', 1.0, 0.85), (17, 1.2, 'E5', 0.8, 0.80), (17, 2.0, 'D5', 1.0, 0.82), (17, 3.0, 'B4', 1.0, 0.78),
        # Bar 18 (C): C5, E5, G5, C6
        (18, 0.0, 'C5', 1.0, 0.86), (18, 1.0, 'E5', 1.0, 0.88), (18, 2.0, 'G5', 1.0, 0.92), (18, 3.0, 'C6', 1.5, 0.96),
        # Bar 19 (C): B5, G5, E5, C5
        (19, 0.0, 'B5', 1.0, 0.85), (19, 1.2, 'G5', 0.8, 0.80), (19, 2.2, 'E5', 1.0, 0.78), (19, 3.2, 'C5', 1.0, 0.75),
        # Bar 20 (F): A4, C5, F5, E5
        (20, 0.0, 'A4', 1.0, 0.80), (20, 1.0, 'C5', 1.0, 0.84), (20, 2.0, 'F5', 1.2, 0.88), (20, 3.2, 'E5', 0.8, 0.80),
        # Bar 21 (G): D5, G4, B4, D5 (Turnaround smooth lead to Bar 0)
        (21, 0.0, 'D5', 1.0, 0.82), (21, 1.2, 'G4', 0.8, 0.76), (21, 2.2, 'B4', 0.8, 0.78), (21, 3.0, 'D5', 1.0, 0.84)
    ]
    
    # Layer 1: Pad & Bass for all 22 bars
    for bar_idx, (pad_pitches, piano_arp, bass_note) in enumerate(chords):
        # Warm pad chord
        c.add_pad(bar_idx, 0, pad_pitches, dur_beats=4.0, vel=0.55, pan=-0.25)
        # Acoustic bass on beat 0 and beat 2
        c.add_note('bass', bar_idx, 0.0, bass_note, dur_beats=1.8, vel=0.78, pan=0.0)
        c.add_note('bass', bar_idx, 2.0, bass_note, dur_beats=1.8, vel=0.65, pan=0.0)
        # Piano broken chord arpeggios
        for beat_sub, p_note in enumerate(piano_arp):
            c.add_note('piano', bar_idx, beat_sub * 1.0, p_note, dur_beats=1.2, vel=0.58, pan=0.35)
            
    # Layer 2: Kalimba melodic lead
    for bar_i, beat_i, pitch, dur_b, vel in kalimba_melody:
        c.add_note('kalimba', bar_i, beat_i, pitch, dur_beats=dur_b, vel=vel, pan=-0.15)
        
    return c.build(wet_reverb=0.22)


def compose_bgm_lesson():
    """
    2. bgm_lesson: Êm dịu, tập trung (Chế độ Hướng dẫn & Bài học)
    Key: F Major | Tempo: 72 BPM | 20 Bars (66.7s)
    Instruments: Music Box chimes + Breathy Warm Pad + Gentle Piano
    """
    c = ScoreComposer(bpm=72, num_bars=20, beats_per_bar=4, tail_bars=1)
    
    chords = [
        # Phrase 1: Bars 0-3 (F -> C/E -> Dm7 -> Bbmaj7)
        (['F3', 'A3', 'C4'], ['F4', 'A4', 'C5'], 'F2'),
        (['E3', 'G3', 'C4'], ['G4', 'C5', 'E5'], 'E2'),
        (['D3', 'F3', 'A3'], ['F4', 'A4', 'D5'], 'D2'),
        (['Bb2', 'D3', 'F3'], ['F4', 'Bb4', 'D5'], 'Bb1'),
        # Phrase 2: Bars 4-7 (F/A -> Gm7 -> Csus4 -> C)
        (['A2', 'C3', 'F3'], ['F4', 'A4', 'C5'], 'A1'),
        (['G2', 'Bb2', 'D3'], ['D4', 'G4', 'Bb4'], 'G1'),
        (['C3', 'F3', 'G3'], ['E4', 'G4', 'C5'], 'C2'),
        (['C3', 'E3', 'G3'], ['E4', 'G4', 'C5'], 'C2'),
        # Phrase 3: Bars 8-11 (Dm7 -> Bbmaj7 -> F -> C)
        (['D3', 'F3', 'A3'], ['F4', 'A4', 'D5'], 'D2'),
        (['Bb2', 'D3', 'F3'], ['F4', 'Bb4', 'D5'], 'Bb1'),
        (['F3', 'A3', 'C4'], ['A4', 'C5', 'F5'], 'F2'),
        (['C3', 'E3', 'G3'], ['G4', 'C5', 'E5'], 'C2'),
        # Phrase 4: Bars 12-15 (Bbmaj7 -> Am7 -> Gm7 -> C)
        (['Bb2', 'D3', 'F3'], ['F4', 'Bb4', 'D5'], 'Bb1'),
        (['A2', 'C3', 'E3'], ['E4', 'A4', 'C5'], 'A1'),
        (['G2', 'Bb2', 'D3'], ['D4', 'G4', 'Bb4'], 'G1'),
        (['C3', 'E3', 'G3'], ['E4', 'G4', 'C5'], 'C2'),
        # Phrase 5: Bars 16-19 (Fmaj7 -> Dm7 -> Bbmaj7 -> C)
        (['F3', 'A3', 'E4'], ['A4', 'C5', 'E5'], 'F2'),
        (['D3', 'F3', 'A3'], ['F4', 'A4', 'D5'], 'D2'),
        (['Bb2', 'D3', 'F3'], ['F4', 'Bb4', 'D5'], 'Bb1'),
        (['C3', 'E3', 'G3'], ['G4', 'C5', 'E5'], 'C2')
    ]
    
    # Music box: delicate, calm, spacious notes so the child can read/listen comfortably
    music_box_notes = [
        # Bar 0: F5, A5, C6
        (0, 0.0, 'F5', 1.8, 0.78), (0, 2.0, 'A5', 1.5, 0.82), (0, 3.0, 'C6', 2.0, 0.85),
        # Bar 1: G5, E5
        (1, 1.0, 'G5', 1.5, 0.80), (1, 2.5, 'E5', 2.0, 0.78),
        # Bar 2: D5, F5, A5
        (2, 0.0, 'D5', 1.8, 0.76), (2, 2.0, 'F5', 1.5, 0.80), (2, 3.0, 'A5', 2.0, 0.84),
        # Bar 3: Bb5, F5
        (3, 1.0, 'Bb5', 1.8, 0.82), (3, 2.5, 'F5', 2.0, 0.78),
        # Bar 4: C6, A5
        (4, 0.0, 'C6', 2.0, 0.84), (4, 2.0, 'A5', 2.0, 0.80),
        # Bar 5: D5, G5, Bb5
        (5, 0.5, 'D5', 1.2, 0.74), (5, 2.0, 'G5', 1.5, 0.78), (5, 3.0, 'Bb5', 1.8, 0.82),
        # Bar 6: C6, G5
        (6, 1.0, 'C6', 2.0, 0.85), (6, 2.5, 'G5', 1.8, 0.80),
        # Bar 7: E5, C5
        (7, 1.0, 'E5', 1.8, 0.78), (7, 2.5, 'C5', 2.2, 0.74),
        # Bar 8: A5, F5, D5
        (8, 0.0, 'A5', 1.8, 0.82), (8, 2.0, 'F5', 1.5, 0.78), (8, 3.0, 'D5', 1.8, 0.75),
        # Bar 9: Bb5, D6, F6
        (9, 1.0, 'Bb5', 1.8, 0.80), (9, 2.5, 'D6', 2.0, 0.84),
        # Bar 10: C6, A5, F5
        (10, 0.0, 'C6', 2.0, 0.85), (10, 2.0, 'A5', 1.5, 0.80), (10, 3.0, 'F5', 2.0, 0.76),
        # Bar 11: G5, E5
        (11, 1.0, 'G5', 2.0, 0.78), (11, 2.5, 'E5', 2.2, 0.75),
        # Bar 12: D6, Bb5
        (12, 0.5, 'D6', 2.0, 0.82), (12, 2.5, 'Bb5', 1.8, 0.78),
        # Bar 13: C6, A5
        (13, 0.5, 'C6', 2.0, 0.82), (13, 2.5, 'A5', 1.8, 0.78),
        # Bar 14: Bb5, G5
        (14, 0.5, 'Bb5', 2.0, 0.80), (14, 2.5, 'G5', 1.8, 0.75),
        # Bar 15: C6, E5
        (15, 0.5, 'C6', 2.0, 0.84), (15, 2.5, 'E5', 2.0, 0.76),
        # Bar 16: F5, A5, C6
        (16, 0.0, 'F5', 1.8, 0.78), (16, 2.0, 'A5', 1.5, 0.82), (16, 3.0, 'C6', 2.2, 0.86),
        # Bar 17: D6, A5
        (17, 1.0, 'D6', 2.0, 0.82), (17, 2.5, 'A5', 1.8, 0.78),
        # Bar 18: Bb5, F5, D5
        (18, 0.5, 'Bb5', 1.8, 0.80), (18, 2.0, 'F5', 1.5, 0.76), (18, 3.0, 'D5', 1.8, 0.74),
        # Bar 19: C5, E5, G5 (Quiet lead back to Bar 0)
        (19, 1.0, 'C5', 1.5, 0.74), (19, 2.2, 'E5', 1.5, 0.76), (19, 3.0, 'G5', 2.0, 0.80)
    ]
    
    for bar_idx, (pad_pitches, piano_arp, bass_note) in enumerate(chords):
        # Soft ambient pad
        c.add_pad(bar_idx, 0, pad_pitches, dur_beats=4.0, vel=0.50, pan=0.0)
        # Deep gentle bass root on beat 0
        c.add_note('bass', bar_idx, 0.0, bass_note, dur_beats=2.5, vel=0.68, pan=0.0)
        # Supportive piano notes
        c.add_note('piano', bar_idx, 1.0, piano_arp[0], dur_beats=1.8, vel=0.50, pan=-0.30)
        c.add_note('piano', bar_idx, 2.5, piano_arp[1], dur_beats=1.8, vel=0.52, pan=0.30)
        
    for bar_i, beat_i, pitch, dur_b, vel in music_box_notes:
        c.add_note('music_box', bar_i, beat_i, pitch, dur_beats=dur_b, vel=vel, pan=0.15)
        
    return c.build(wet_reverb=0.26)


def compose_bgm_dino():
    """
    3. bgm_dino: Thư thái, dễ thương (Đấu với Khủng Long - Dễ)
    Key: G Major | Tempo: 76 BPM | 20 Bars (63.2s)
    Instruments: Bouncy Kalimba + Glockenspiel/Music Box + Playful Bass
    """
    c = ScoreComposer(bpm=76, num_bars=20, beats_per_bar=4, tail_bars=1)
    
    chords = [
        # Phrase 1: Bars 0-3 (G -> C -> G/B -> D)
        (['G3', 'B3', 'D4'], ['B3', 'D4', 'G4'], 'G2'),
        (['G3', 'C4', 'E4'], ['C4', 'E4', 'G4'], 'C2'),
        (['G3', 'B3', 'D4'], ['D4', 'G4', 'B4'], 'B1'),
        (['F#3', 'A3', 'D4'], ['A3', 'D4', 'F#4'], 'D2'),
        # Phrase 2: Bars 4-7 (Em -> C -> Am7 -> D)
        (['E3', 'G3', 'B3'], ['G3', 'B3', 'E4'], 'E2'),
        (['G3', 'C4', 'E4'], ['C4', 'E4', 'G4'], 'C2'),
        (['A2', 'C3', 'E3'], ['C4', 'E4', 'A4'], 'A1'),
        (['F#3', 'A3', 'D4'], ['A3', 'D4', 'F#4'], 'D2'),
        # Phrase 3: Bars 8-11 (G -> Bm7 -> C -> D)
        (['G3', 'B3', 'D4'], ['B3', 'D4', 'G4'], 'G2'),
        (['F#3', 'B3', 'D4'], ['B3', 'D4', 'F#4'], 'B1'),
        (['G3', 'C4', 'E4'], ['C4', 'E4', 'G4'], 'C2'),
        (['F#3', 'A3', 'D4'], ['A3', 'D4', 'F#4'], 'D2'),
        # Phrase 4: Bars 12-15 (C -> D -> Bm7 -> Em)
        (['G3', 'C4', 'E4'], ['C4', 'E4', 'G4'], 'C2'),
        (['F#3', 'A3', 'D4'], ['A3', 'D4', 'F#4'], 'D2'),
        (['F#3', 'B3', 'D4'], ['B3', 'D4', 'F#4'], 'B1'),
        (['E3', 'G3', 'B3'], ['G3', 'B3', 'E4'], 'E2'),
        # Phrase 5: Bars 16-19 (Am7 -> D7 -> G -> G)
        (['A2', 'C3', 'E3'], ['C4', 'E4', 'A4'], 'A1'),
        (['F#3', 'C4', 'D4'], ['A3', 'C4', 'D4'], 'D2'),
        (['G3', 'B3', 'D4'], ['B4', 'D5', 'G5'], 'G2'),
        (['G3', 'B3', 'D4'], ['B4', 'D5', 'G5'], 'G2')
    ]
    
    dino_melody = [
        # Bar 0: D5, B4, G4, A4, B4
        (0, 0.0, 'D5', 0.8, 0.85), (0, 1.0, 'B4', 0.8, 0.80), (0, 2.0, 'G4', 0.6, 0.78), (0, 2.8, 'A4', 0.6, 0.80), (0, 3.5, 'B4', 0.8, 0.84),
        # Bar 1: C5, E5, G5
        (1, 0.0, 'C5', 1.0, 0.86), (1, 1.5, 'E5', 1.0, 0.88), (1, 2.8, 'G5', 1.2, 0.92),
        # Bar 2: D5, B4, G4
        (2, 0.0, 'D5', 1.0, 0.84), (2, 1.2, 'B4', 0.8, 0.80), (2, 2.5, 'G4', 1.2, 0.78),
        # Bar 3: A4, F#4, D4, A4
        (3, 0.0, 'A4', 1.0, 0.82), (3, 1.2, 'F#4', 0.8, 0.78), (3, 2.2, 'D4', 0.8, 0.76), (3, 3.2, 'A4', 0.8, 0.80),
        # Bar 4: B4, G4, E4, G4, B4
        (4, 0.0, 'B4', 0.8, 0.82), (4, 1.0, 'G4', 0.8, 0.78), (4, 2.0, 'E4', 0.6, 0.75), (4, 2.8, 'G4', 0.6, 0.78), (4, 3.5, 'B4', 0.8, 0.82),
        # Bar 5: C5, E5, D5, C5
        (5, 0.0, 'C5', 1.0, 0.85), (5, 1.2, 'E5', 1.0, 0.88), (5, 2.2, 'D5', 0.8, 0.82), (5, 3.2, 'C5', 0.8, 0.80),
        # Bar 6: A4, C5, E5
        (6, 0.0, 'A4', 1.0, 0.80), (6, 1.2, 'C5', 1.0, 0.84), (6, 2.5, 'E5', 1.2, 0.88),
        # Bar 7: D5, F#5, A5
        (7, 0.0, 'D5', 1.0, 0.85), (7, 1.2, 'F#5', 1.0, 0.90), (7, 2.5, 'A5', 1.2, 0.94),
        # Bar 8: B5, G5, D5, B4
        (8, 0.0, 'B5', 1.0, 0.90), (8, 1.2, 'G5', 0.8, 0.84), (8, 2.2, 'D5', 0.8, 0.80), (8, 3.2, 'B4', 0.8, 0.78),
        # Bar 9: F#5, D5, B4
        (9, 0.0, 'F#5', 1.2, 0.86), (9, 1.5, 'D5', 0.8, 0.80), (9, 2.5, 'B4', 1.2, 0.78),
        # Bar 10: E5, G5, C6
        (10, 0.0, 'E5', 1.0, 0.84), (10, 1.2, 'G5', 1.0, 0.88), (10, 2.5, 'C6', 1.4, 0.94),
        # Bar 11: B5, A5, G5, F#5
        (11, 0.0, 'B5', 1.0, 0.88), (11, 1.2, 'A5', 0.8, 0.84), (11, 2.2, 'G5', 0.8, 0.82), (11, 3.2, 'F#5', 0.8, 0.80),
        # Bar 12: G5, E5, C5
        (12, 0.0, 'G5', 1.2, 0.86), (12, 1.5, 'E5', 0.8, 0.82), (12, 2.5, 'C5', 1.2, 0.80),
        # Bar 13: A5, F#5, D5
        (13, 0.0, 'A5', 1.2, 0.88), (13, 1.5, 'F#5', 0.8, 0.84), (13, 2.5, 'D5', 1.2, 0.82),
        # Bar 14: B5, G5, D5
        (14, 0.0, 'B5', 1.2, 0.90), (14, 1.5, 'G5', 0.8, 0.85), (14, 2.5, 'D5', 1.2, 0.82),
        # Bar 15: E5, G4, B4, E5
        (15, 0.0, 'E5', 1.2, 0.84), (15, 1.5, 'G4', 0.8, 0.78), (15, 2.5, 'B4', 0.8, 0.80), (15, 3.2, 'E5', 0.8, 0.82),
        # Bar 16: C5, E5, A5
        (16, 0.0, 'C5', 1.0, 0.82), (16, 1.2, 'E5', 1.0, 0.86), (16, 2.5, 'A5', 1.2, 0.90),
        # Bar 17: F#5, D5, C5, A4
        (17, 0.0, 'F#5', 1.0, 0.85), (17, 1.2, 'D5', 0.8, 0.80), (17, 2.2, 'C5', 0.8, 0.78), (17, 3.2, 'A4', 0.8, 0.76),
        # Bar 18: B4, D5, G5, B5
        (18, 0.0, 'B4', 0.8, 0.82), (18, 1.0, 'D5', 0.8, 0.84), (18, 2.0, 'G5', 1.0, 0.88), (18, 3.0, 'B5', 1.4, 0.92),
        # Bar 19: G5, D5, B4, G4 (Cute resolution back to Bar 0)
        (19, 0.0, 'G5', 1.0, 0.86), (19, 1.2, 'D5', 0.8, 0.80), (19, 2.2, 'B4', 0.8, 0.78), (19, 3.2, 'G4', 1.0, 0.75)
    ]
    
    for bar_idx, (pad_pitches, piano_arp, bass_note) in enumerate(chords):
        c.add_pad(bar_idx, 0, pad_pitches, dur_beats=4.0, vel=0.52, pan=-0.20)
        # Bouncy bass: beat 0 and beat 2.5
        c.add_note('bass', bar_idx, 0.0, bass_note, dur_beats=1.5, vel=0.76, pan=0.0)
        c.add_note('bass', bar_idx, 2.0, bass_note, dur_beats=1.2, vel=0.68, pan=0.0)
        # Gentle piano chime
        c.add_note('piano', bar_idx, 1.0, piano_arp[0], dur_beats=1.0, vel=0.52, pan=0.30)
        c.add_note('piano', bar_idx, 3.0, piano_arp[1], dur_beats=1.0, vel=0.50, pan=0.30)
        
    for bar_i, beat_i, pitch, dur_b, vel in dino_melody:
        c.add_note('kalimba', bar_i, beat_i, pitch, dur_beats=dur_b, vel=vel, pan=-0.10)
        # Glockenspiel / Music box accent on key high notes
        if pitch in ['G5', 'B5', 'C6', 'A5']:
            c.add_note('music_box', bar_i, beat_i + 0.02, pitch, dur_beats=dur_b * 1.2, vel=vel * 0.75, pan=0.25)
            
    return c.build(wet_reverb=0.20)


def compose_bgm_kingkong():
    """
    4. bgm_kingkong: Nhẹ nhàng, sôi nổi (Đấu với King Kong - Vừa)
    Key: D Major | Tempo: 82 BPM | 24 Bars (70.2s)
    Instruments: Wooden Marimba + Kalimba + Piano syncopation + Bouncy walking bass
    """
    c = ScoreComposer(bpm=82, num_bars=24, beats_per_bar=4, tail_bars=1)
    
    chords = [
        # Phrase 1: Bars 0-3 (D -> G -> A -> D)
        (['D3', 'F#3', 'A3'], ['F#4', 'A4', 'D5'], 'D2'),
        (['G3', 'B3', 'D4'], ['G4', 'B4', 'D5'], 'G2'),
        (['A3', 'C#4', 'E4'], ['A4', 'C#5', 'E5'], 'A2'),
        (['D3', 'F#3', 'A3'], ['F#4', 'A4', 'D5'], 'D2'),
        # Phrase 2: Bars 4-7 (Bm -> G -> Em7 -> A)
        (['B2', 'D3', 'F#3'], ['F#4', 'B4', 'D5'], 'B1'),
        (['G2', 'B2', 'D3'], ['G4', 'B4', 'D5'], 'G2'),
        (['E3', 'G3', 'B3'], ['G4', 'B4', 'E5'], 'E2'),
        (['A2', 'C#3', 'E3'], ['A4', 'C#5', 'E5'], 'A2'),
        # Phrase 3: Bars 8-11 (D -> F#m -> G -> A)
        (['D3', 'F#3', 'A3'], ['F#4', 'A4', 'D5'], 'D2'),
        (['F#2', 'A2', 'C#3'], ['A4', 'C#5', 'F#5'], 'F#2'),
        (['G2', 'B2', 'D3'], ['G4', 'B4', 'D5'], 'G2'),
        (['A2', 'C#3', 'E3'], ['A4', 'C#5', 'E5'], 'A2'),
        # Phrase 4: Bars 12-15 (G -> A -> F#m -> Bm)
        (['G2', 'B2', 'D3'], ['G4', 'B4', 'D5'], 'G2'),
        (['A2', 'C#3', 'E3'], ['A4', 'C#5', 'E5'], 'A2'),
        (['F#2', 'A2', 'C#3'], ['A4', 'C#5', 'F#5'], 'F#2'),
        (['B2', 'D3', 'F#3'], ['F#4', 'B4', 'D5'], 'B1'),
        # Phrase 5: Bars 16-19 (Em7 -> A7 -> D -> Bm)
        (['E3', 'G3', 'B3'], ['G4', 'B4', 'E5'], 'E2'),
        (['A2', 'C#3', 'G3'], ['G4', 'A4', 'C#5'], 'A2'),
        (['D3', 'F#3', 'A3'], ['F#4', 'A4', 'D5'], 'D2'),
        (['B2', 'D3', 'F#3'], ['F#4', 'B4', 'D5'], 'B1'),
        # Phrase 6: Bars 20-23 (Gmaj7 -> A7 -> D -> D)
        (['G2', 'B2', 'F#3'], ['G4', 'B4', 'D5'], 'G2'),
        (['A2', 'C#3', 'G3'], ['G4', 'A4', 'C#5'], 'A2'),
        (['D3', 'F#3', 'A3'], ['F#4', 'A4', 'D5'], 'D2'),
        (['D3', 'F#3', 'A3'], ['F#4', 'A4', 'D5'], 'D2')
    ]
    
    # Marimba rhythmic patterns (wooden warmth)
    for bar_idx, (pad_pitches, piano_arp, bass_note) in enumerate(chords):
        c.add_pad(bar_idx, 0, pad_pitches, dur_beats=4.0, vel=0.50, pan=-0.25)
        # Walking bassline
        c.add_note('bass', bar_idx, 0.0, bass_note, dur_beats=1.2, vel=0.76, pan=0.0)
        c.add_note('bass', bar_idx, 1.5, bass_note, dur_beats=1.0, vel=0.68, pan=0.0)
        c.add_note('bass', bar_idx, 2.5, bass_note, dur_beats=1.2, vel=0.72, pan=0.0)
        
        # Marimba syncopated pattern
        c.add_note('marimba', bar_idx, 0.0, piano_arp[0], dur_beats=0.6, vel=0.68, pan=0.25)
        c.add_note('marimba', bar_idx, 0.75, piano_arp[1], dur_beats=0.6, vel=0.65, pan=0.30)
        c.add_note('marimba', bar_idx, 1.5, piano_arp[2], dur_beats=0.6, vel=0.70, pan=0.25)
        c.add_note('marimba', bar_idx, 2.5, piano_arp[1], dur_beats=0.6, vel=0.66, pan=0.30)
        c.add_note('marimba', bar_idx, 3.25, piano_arp[0], dur_beats=0.6, vel=0.64, pan=0.25)
        
    # Kalimba spirited lead
    kingkong_melody = [
        # Bar 0: D5, F#5, A5
        (0, 0.0, 'D5', 0.8, 0.86), (0, 1.0, 'F#5', 0.8, 0.88), (0, 2.0, 'A5', 1.2, 0.92),
        # Bar 1: B5, A5, G5, F#5
        (1, 0.0, 'B5', 1.0, 0.90), (1, 1.2, 'A5', 0.8, 0.84), (1, 2.2, 'G5', 0.8, 0.82), (1, 3.2, 'F#5', 0.8, 0.80),
        # Bar 2: E5, A4, C#5, E5
        (2, 0.0, 'E5', 1.0, 0.84), (2, 1.2, 'A4', 0.8, 0.78), (2, 2.2, 'C#5', 0.8, 0.80), (2, 3.2, 'E5', 0.8, 0.84),
        # Bar 3: D5, F#5, D5
        (3, 0.0, 'D5', 1.5, 0.88), (3, 2.0, 'F#5', 1.0, 0.84), (3, 3.2, 'D5', 0.8, 0.80),
        # Bar 4: F#5, D5, B4, D5
        (4, 0.0, 'F#5', 1.0, 0.86), (4, 1.2, 'D5', 0.8, 0.82), (4, 2.2, 'B4', 0.8, 0.80), (4, 3.2, 'D5', 0.8, 0.84),
        # Bar 5: G5, B5, D6
        (5, 0.0, 'G5', 1.0, 0.88), (5, 1.2, 'B5', 1.0, 0.92), (5, 2.5, 'D6', 1.4, 0.96),
        # Bar 6: E5, G5, B5
        (6, 0.0, 'E5', 1.0, 0.84), (6, 1.2, 'G5', 1.0, 0.86), (6, 2.5, 'B5', 1.2, 0.90),
        # Bar 7: A5, C#6, E6
        (7, 0.0, 'A5', 1.0, 0.88), (7, 1.2, 'C#6', 1.0, 0.92), (7, 2.5, 'E6', 1.4, 0.95),
        # Bar 8: D6, A5, F#5
        (8, 0.0, 'D6', 1.2, 0.92), (8, 1.5, 'A5', 0.8, 0.86), (8, 2.5, 'F#5', 1.2, 0.84),
        # Bar 9: C#6, A5, F#5
        (9, 0.0, 'C#6', 1.2, 0.88), (9, 1.5, 'A5', 0.8, 0.84), (9, 2.5, 'F#5', 1.2, 0.82),
        # Bar 10: B5, G5, D5
        (10, 0.0, 'B5', 1.2, 0.90), (10, 1.5, 'G5', 0.8, 0.84), (10, 2.5, 'D5', 1.2, 0.82),
        # Bar 11: A5, E5, C#5
        (11, 0.0, 'A5', 1.2, 0.88), (11, 1.5, 'E5', 0.8, 0.82), (11, 2.5, 'C#5', 1.2, 0.80),
        # Bar 12: B5, D6, G5
        (12, 0.0, 'B5', 1.0, 0.88), (12, 1.2, 'D6', 1.0, 0.92), (12, 2.5, 'G5', 1.2, 0.85),
        # Bar 13: C#6, E6, A5
        (13, 0.0, 'C#6', 1.0, 0.90), (13, 1.2, 'E6', 1.0, 0.94), (13, 2.5, 'A5', 1.2, 0.86),
        # Bar 14: A5, F#5, D5
        (14, 0.0, 'A5', 1.2, 0.88), (14, 1.5, 'F#5', 0.8, 0.84), (14, 2.5, 'D5', 1.2, 0.82),
        # Bar 15: B4, D5, F#5
        (15, 0.0, 'B4', 1.0, 0.82), (15, 1.2, 'D5', 1.0, 0.85), (15, 2.5, 'F#5', 1.2, 0.88),
        # Bar 16: G5, E5, B4
        (16, 0.0, 'G5', 1.0, 0.85), (16, 1.2, 'E5', 0.8, 0.80), (16, 2.2, 'B4', 1.2, 0.78),
        # Bar 17: A5, G5, F#5, E5
        (17, 0.0, 'A5', 1.0, 0.88), (17, 1.2, 'G5', 0.8, 0.84), (17, 2.2, 'F#5', 0.8, 0.82), (17, 3.2, 'E5', 0.8, 0.80),
        # Bar 18: F#5, D5, A4
        (18, 0.0, 'F#5', 1.2, 0.86), (18, 1.5, 'D5', 0.8, 0.82), (18, 2.5, 'A4', 1.2, 0.80),
        # Bar 19: B4, D5, F#5
        (19, 0.0, 'B4', 1.0, 0.82), (19, 1.2, 'D5', 1.0, 0.84), (19, 2.5, 'F#5', 1.2, 0.88),
        # Bar 20: G5, B5, D6
        (20, 0.0, 'G5', 1.0, 0.86), (20, 1.2, 'B5', 1.0, 0.90), (20, 2.5, 'D6', 1.2, 0.94),
        # Bar 21: C#6, A5, G5, E5
        (21, 0.0, 'C#6', 1.0, 0.88), (21, 1.2, 'A5', 0.8, 0.84), (21, 2.2, 'G5', 0.8, 0.80), (21, 3.2, 'E5', 0.8, 0.78),
        # Bar 22: F#5, A5, D6
        (22, 0.0, 'F#5', 1.0, 0.88), (22, 1.2, 'A5', 1.0, 0.90), (22, 2.5, 'D6', 1.5, 0.95),
        # Bar 23: A5, F#5, D5, A4 (Lead cleanly back to Bar 0)
        (23, 0.0, 'A5', 1.0, 0.85), (23, 1.2, 'F#5', 0.8, 0.80), (23, 2.2, 'D5', 0.8, 0.78), (23, 3.2, 'A4', 0.8, 0.75)
    ]
    
    for bar_i, beat_i, pitch, dur_b, vel in kingkong_melody:
        c.add_note('kalimba', bar_i, beat_i, pitch, dur_beats=dur_b, vel=vel, pan=-0.15)
        
    return c.build(wet_reverb=0.20)


def compose_bgm_godzilla():
    """
    5. bgm_godzilla: Trầm, hơi hồi hộp nhưng êm (Đấu với Godzilla - Khó)
    Key: D Dorian / D Minor | Tempo: 74 BPM | 22 Bars (71.4s)
    Instruments: Deep Soft Piano chords + High Music Box chimes + Low Ambient Pad + Deep Bass
    """
    c = ScoreComposer(bpm=74, num_bars=22, beats_per_bar=4, tail_bars=1)
    
    chords = [
        # Phrase 1: Bars 0-3 (Dm -> C -> Bb -> Am)
        (['D3', 'F3', 'A3'], ['F3', 'A3', 'D4'], 'D2'),
        (['C3', 'E3', 'G3'], ['E3', 'G3', 'C4'], 'C2'),
        (['Bb2', 'D3', 'F3'], ['D3', 'F3', 'Bb3'], 'Bb1'),
        (['A2', 'C3', 'E3'], ['C3', 'E3', 'A3'], 'A1'),
        # Phrase 2: Bars 4-7 (Dm -> G/B -> Bbmaj7 -> A7sus4)
        (['D3', 'F3', 'A3'], ['F3', 'A3', 'D4'], 'D2'),
        (['B2', 'D3', 'G3'], ['G3', 'B3', 'D4'], 'B1'),
        (['Bb2', 'D3', 'F3'], ['F3', 'A3', 'D4'], 'Bb1'),
        (['A2', 'D3', 'E3'], ['E3', 'G3', 'A3'], 'A1'),
        # Phrase 3: Bars 8-11 (Dm -> F -> C -> Dm)
        (['D3', 'F3', 'A3'], ['A3', 'D4', 'F4'], 'D2'),
        (['F2', 'A2', 'C3'], ['A3', 'C4', 'F4'], 'F2'),
        (['C3', 'E3', 'G3'], ['G3', 'C4', 'E4'], 'C2'),
        (['D3', 'F3', 'A3'], ['F3', 'A3', 'D4'], 'D2'),
        # Phrase 4: Bars 12-15 (Bb -> C -> Dm -> Dm)
        (['Bb2', 'D3', 'F3'], ['D4', 'F4', 'Bb4'], 'Bb1'),
        (['C3', 'E3', 'G3'], ['E4', 'G4', 'C5'], 'C2'),
        (['D3', 'F3', 'A3'], ['F4', 'A4', 'D5'], 'D2'),
        (['D3', 'F3', 'A3'], ['F4', 'A4', 'D5'], 'D2'),
        # Phrase 5: Bars 16-19 (Gm7 -> Am7 -> Bbmaj7 -> C)
        (['G2', 'Bb2', 'D3'], ['D4', 'G4', 'Bb4'], 'G1'),
        (['A2', 'C3', 'E3'], ['E4', 'A4', 'C5'], 'A1'),
        (['Bb2', 'D3', 'F3'], ['F4', 'Bb4', 'D5'], 'Bb1'),
        (['C3', 'E3', 'G3'], ['G4', 'C5', 'E5'], 'C2'),
        # Phrase 6: Bars 20-21 (Bbmaj7 -> C7 leading back to Dm)
        (['Bb2', 'D3', 'F3'], ['F4', 'A4', 'D5'], 'Bb1'),
        (['C3', 'E3', 'G3'], ['E4', 'G4', 'C5'], 'C2')
    ]
    
    # Music box high twinkling counterpoint (like distant stars over deep calm waters)
    godzilla_chimes = [
        # Bar 0: D5, F5, A5
        (0, 0.0, 'D5', 2.0, 0.76), (0, 2.0, 'F5', 1.8, 0.78), (0, 3.0, 'A5', 2.2, 0.82),
        # Bar 1: G5, E5, C5
        (1, 1.0, 'G5', 1.8, 0.76), (1, 2.5, 'E5', 1.8, 0.74), (1, 3.5, 'C5', 2.0, 0.72),
        # Bar 2: F5, D5, Bb4
        (2, 0.5, 'F5', 1.8, 0.75), (2, 2.0, 'D5', 1.8, 0.74), (2, 3.2, 'Bb4', 2.0, 0.70),
        # Bar 3: E5, C#5, A4
        (3, 1.0, 'E5', 2.0, 0.76), (3, 2.5, 'C#5', 2.2, 0.74),
        # Bar 4: D5, A5, D6
        (4, 0.0, 'D5', 1.8, 0.78), (4, 1.5, 'A5', 1.8, 0.82), (4, 3.0, 'D6', 2.2, 0.86),
        # Bar 5: B5, G5, D5
        (5, 1.0, 'B5', 2.0, 0.80), (5, 2.5, 'G5', 2.0, 0.76),
        # Bar 6: F5, A5, D6
        (6, 0.5, 'F5', 1.8, 0.78), (6, 2.0, 'A5', 1.8, 0.82), (6, 3.2, 'D6', 2.2, 0.85),
        # Bar 7: E5, A5, C#6
        (7, 1.0, 'E5', 2.0, 0.78), (7, 2.5, 'C#6', 2.5, 0.84),
        # Bar 8: D6, A5, F5
        (8, 0.0, 'D6', 2.0, 0.84), (8, 2.0, 'A5', 1.8, 0.80), (8, 3.0, 'F5', 2.0, 0.76),
        # Bar 9: C6, A5, F5
        (9, 1.0, 'C6', 2.0, 0.82), (9, 2.5, 'A5', 2.0, 0.78),
        # Bar 10: E6, C6, G5
        (10, 0.5, 'E6', 2.0, 0.85), (10, 2.0, 'C6', 1.8, 0.80), (10, 3.2, 'G5', 2.0, 0.76),
        # Bar 11: D6, A5, F5
        (11, 1.0, 'D6', 2.5, 0.84), (11, 2.8, 'F5', 2.0, 0.76),
        # Bar 12: F6, D6, Bb5
        (12, 0.5, 'F6', 2.0, 0.86), (12, 2.2, 'D6', 1.8, 0.80), (12, 3.2, 'Bb5', 2.0, 0.78),
        # Bar 13: G6, E6, C6
        (13, 0.5, 'G6', 2.0, 0.88), (13, 2.2, 'E6', 1.8, 0.82), (13, 3.2, 'C6', 2.0, 0.80),
        # Bar 14: A6, F6, D6
        (14, 0.0, 'A6', 2.2, 0.90), (14, 2.0, 'F6', 1.8, 0.84), (14, 3.0, 'D6', 2.2, 0.82),
        # Bar 15: D6, A5, F5
        (15, 1.0, 'D6', 2.0, 0.80), (15, 2.5, 'A5', 2.2, 0.76),
        # Bar 16: Bb5, D6, G6
        (16, 0.5, 'Bb5', 1.8, 0.78), (16, 2.0, 'D6', 1.8, 0.82), (16, 3.2, 'G6', 2.2, 0.86),
        # Bar 17: C6, E6, A6
        (17, 0.5, 'C6', 1.8, 0.80), (17, 2.0, 'E6', 1.8, 0.84), (17, 3.2, 'A6', 2.2, 0.88),
        # Bar 18: D6, F6, Bb6
        (18, 0.5, 'D6', 2.0, 0.82), (18, 2.0, 'F6', 1.8, 0.86), (18, 3.2, 'Bb6', 2.2, 0.90),
        # Bar 19: E6, C6, G5
        (19, 0.5, 'E6', 2.0, 0.84), (19, 2.0, 'C6', 1.8, 0.80), (19, 3.2, 'G5', 2.0, 0.76),
        # Bar 20: D6, Bb5, F5
        (20, 0.5, 'D6', 2.0, 0.82), (20, 2.0, 'Bb5', 1.8, 0.78), (20, 3.2, 'F5', 2.0, 0.75),
        # Bar 21: E5, G5, C#6 (Mysterious gentle turnaround)
        (21, 1.0, 'E5', 1.8, 0.75), (21, 2.2, 'G5', 1.8, 0.78), (21, 3.2, 'C#6', 2.4, 0.82)
    ]
    
    for bar_idx, (pad_pitches, piano_arp, bass_note) in enumerate(chords):
        # Deep warm ambient pad
        c.add_pad(bar_idx, 0, pad_pitches, dur_beats=4.0, vel=0.54, pan=0.10)
        # Deep bass root note
        c.add_note('bass', bar_idx, 0.0, bass_note, dur_beats=2.8, vel=0.82, pan=0.0)
        # Low soft piano chords
        c.add_note('piano', bar_idx, 0.0, piano_arp[0], dur_beats=2.0, vel=0.62, pan=-0.25)
        c.add_note('piano', bar_idx, 1.5, piano_arp[1], dur_beats=1.8, vel=0.58, pan=-0.20)
        c.add_note('piano', bar_idx, 2.5, piano_arp[2], dur_beats=2.0, vel=0.64, pan=-0.25)
        
    for bar_i, beat_i, pitch, dur_b, vel in godzilla_chimes:
        c.add_note('music_box', bar_i, beat_i, pitch, dur_beats=dur_b, vel=vel, pan=0.25)
        
    return c.build(wet_reverb=0.28)


# ------------------------------------------------------------------------------
# 6. Hậu Kỳ FFmpeg Chuẩn Hóa EBU R128 (-23 LUFS) & Kiểm Tra Thống Kê
# ------------------------------------------------------------------------------

def save_wav_and_encode(name, audio_samples):
    """
    1. Ghi tệp PCM WAV 16-bit tạm thời.
    2. Mã hóa FFmpeg AAC ~84-88 kbps chuẩn hóa EBU R128 (-23 LUFS, True Peak <= -2 dBTP).
    3. Trả về thông số đo đạc ffprobe / loudnorm.
    """
    wav_path = os.path.join(TMP_DIR, f"{name}.wav")
    m4a_path = os.path.join(RAW_DIR, f"{name}.m4a")
    
    # Kẹp mẫu âm trong dải 16-bit
    clamped = np.clip(audio_samples * 32767.0, -32768, 32767).astype(np.int16)
    
    with wave.open(wav_path, "wb") as wf:
        wf.setnchannels(2)
        wf.setsampwidth(2)
        wf.setframerate(SAMPLE_RATE)
        wf.writeframes(clamped.tobytes())
        
    # FFmpeg Post-process: loudnorm I=-23:TP=-2:LRA=7
    cmd = [
        FFMPEG_BIN, "-y", "-v", "error",
        "-i", wav_path,
        "-af", "loudnorm=I=-23:TP=-2:LRA=7",
        "-c:a", "aac", "-b:a", "84k",
        "-ar", "44100",
        m4a_path
    ]
    subprocess.run(cmd, check=True)
    os.remove(wav_path)
    
    # Đo đạc kiểm chứng thông số chuẩn hóa của file m4a vừa tạo
    measure_cmd = [
        FFMPEG_BIN, "-i", m4a_path,
        "-af", "loudnorm=I=-23:TP=-2:LRA=7:print_format=json",
        "-f", "null", "-"
    ]
    res = subprocess.run(measure_cmd, stderr=subprocess.PIPE, text=True)
    
    lines = res.stderr.splitlines()
    json_lines = []
    capturing = False
    for l in lines:
        if '{' in l:
            capturing = True
        if capturing:
            json_lines.append(l)
        if capturing and '}' in l:
            break
            
    stats = {}
    if json_lines:
        try:
            stats = json.loads('\n'.join(json_lines))
        except Exception:
            pass
            
    file_size = os.path.getsize(m4a_path)
    
    # Lấy thời lượng từ ffprobe
    probe_cmd = [
        "ffprobe", "-v", "error",
        "-show_entries", "format=duration",
        "-of", "default=noprint_wrappers=1:nokey=1",
        m4a_path
    ]
    dur_str = subprocess.check_output(probe_cmd, text=True).strip()
    duration = float(dur_str) if dur_str else 0.0
    
    lufs = stats.get('input_i', 'N/A')
    tp = stats.get('input_tp', 'N/A')
    
    print(f" -> [XONG] {name}.m4a: Thời lượng = {duration:.2f}s | Kích thước = {file_size / 1024:.1f} KB | Âm lượng = {lufs} LUFS (TP = {tp} dBTP)")
    
    return {
        'name': f"{name}.m4a",
        'path': m4a_path,
        'duration_s': duration,
        'size_bytes': file_size,
        'size_kb': file_size / 1024,
        'lufs': lufs,
        'true_peak': tp
    }


def main():
    print("==========================================================================")
    print("BẮT ĐẦU TỔNG HỢP 5 BẢN NHẠC NỀN THỦ TỤC THƯ GIÃN (BGM)")
    print("DỰ ÁN 'ĐẬU HỌC CỜ VUA' - CHUẨN EBU R128 (-23 LUFS), SEAMLESS LOOPS")
    print("==========================================================================")
    
    tracks_info = []
    
    print("\n1/5. Đang tổng hợp bgm_home (Vui tươi, ấm áp - C Major, 80 BPM)...")
    audio, _ = compose_bgm_home()
    info = save_wav_and_encode("bgm_home", audio)
    tracks_info.append(info)
    
    print("\n2/5. Đang tổng hợp bgm_lesson (Êm dịu, tập trung - F Major, 72 BPM)...")
    audio, _ = compose_bgm_lesson()
    info = save_wav_and_encode("bgm_lesson", audio)
    tracks_info.append(info)
    
    print("\n3/5. Đang tổng hợp bgm_dino (Thư thái, dễ thương - G Major, 76 BPM)...")
    audio, _ = compose_bgm_dino()
    info = save_wav_and_encode("bgm_dino", audio)
    tracks_info.append(info)
    
    print("\n4/5. Đang tổng hợp bgm_kingkong (Nhẹ nhàng, sôi nổi - D Major, 82 BPM)...")
    audio, _ = compose_bgm_kingkong()
    info = save_wav_and_encode("bgm_kingkong", audio)
    tracks_info.append(info)
    
    print("\n5/5. Đang tổng hợp bgm_godzilla (Trầm, hơi hồi hộp - D Dorian, 74 BPM)...")
    audio, _ = compose_bgm_godzilla()
    info = save_wav_and_encode("bgm_godzilla", audio)
    tracks_info.append(info)
    
    total_size_bytes = sum(t['size_bytes'] for t in tracks_info)
    total_size_mb = total_size_bytes / (1024 * 1024)
    
    print("\n==========================================================================")
    print("BÁO CÁO THỐNG KÊ CHI TIẾT 5 BẢN NHẠC NỀN BGM")
    print("==========================================================================")
    print(f"{'Tên Tệp':<20} | {'Thời Lượng':<12} | {'Kích Thước':<12} | {'Độ Lớn (LUFS)':<15} | {'True Peak'}")
    print("-" * 75)
    for t in tracks_info:
        print(f"{t['name']:<20} | {t['duration_s']:>6.2f} s    | {t['size_kb']:>6.1f} KB    | {t['lufs']:>8} LUFS   | {t['true_peak']} dBTP")
    print("-" * 75)
    print(f"Tổng dung lượng 5 file: {total_size_mb:.2f} MB (Mục tiêu yêu cầu: < 4.00 MB - ĐẠT: {total_size_mb < 4.0})")
    print("==========================================================================")


if __name__ == "__main__":
    main()
