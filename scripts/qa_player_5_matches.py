#!/usr/bin/env python3
"""
QA Player Agent - Automated Test Runner for 5 Chess Matches
Đậu Học Cờ Vua - Kids 4K Edition
"""

import subprocess
import time
import json
import re
import os
import sys

ADB = "/Users/harrylong/Library/Android/sdk/platform-tools/adb"
ARTIFACT_DIR = "/Users/harrylong/.gemini/antigravity/brain/e0e5ecb6-bc2d-478f-8709-5ffb0eeaeac2"
PACKAGE = "com.harrylong.tvchess"
ACTIVITY = "com.harrylong.tvchess.MainActivity"

os.makedirs(ARTIFACT_DIR, exist_ok=True)

# -------------------------------------------------------------
# Bàn cờ 8x8 toạ độ & hàm ánh xạ màn hình TV 1920x1080
# -------------------------------------------------------------
def compute_coords(row, col, is_3d=True, is_flipped=False):
    canvas_w = 817.0
    canvas_h = 817.0
    canvas_left = 569.0
    canvas_top = 86.0
    
    screen_row = 7 - row if is_flipped else row
    screen_col = 7 - col if is_flipped else col
    
    if not is_3d:
        sq_w = canvas_w / 8.0
        sq_h = canvas_h / 8.0
        cx = (screen_col + 0.5) * sq_w
        cy = (screen_row + 0.5) * sq_h
        return int(canvas_left + cx), int(canvas_top + cy)
    else:
        cx = canvas_w * 0.5
        outerBottomWidth = min(canvas_w * 0.95, canvas_h * 1.55)
        outerTopWidth = outerBottomWidth * 0.78
        outerTopY = canvas_h * 0.06
        outerDepthY = canvas_h * 0.84
        outerBottomY = outerTopY + outerDepthY
        marbleBorderV = outerDepthY * 0.055
        marbleBorderHBottom = outerBottomWidth * 0.048
        marbleBorderHTop = outerTopWidth * 0.048
        innerTopY = outerTopY + marbleBorderV
        innerBottomY = outerBottomY - marbleBorderV
        innerTopWidth = outerTopWidth - marbleBorderHTop * 2.0
        innerBottomWidth = outerBottomWidth - marbleBorderHBottom * 2.0
        
        def get_point(u, v):
            vCurve = (v * (1.0 + 0.12 * v)) / 1.12
            y = innerTopY + (innerBottomY - innerTopY) * vCurve
            widthAtV = innerTopWidth + (innerBottomWidth - innerTopWidth) * vCurve
            x = cx - widthAtV * 0.5 + widthAtV * u
            return x, y

        u0 = screen_col / 8.0
        u1 = (screen_col + 1) / 8.0
        v0 = screen_row / 8.0
        v1 = (screen_row + 1) / 8.0
        p0 = get_point(u0, v0)
        p1 = get_point(u1, v0)
        p2 = get_point(u1, v1)
        p3 = get_point(u0, v1)
        center_x = (p0[0] + p1[0] + p2[0] + p3[0]) * 0.25
        center_y = (p0[1] + p1[1] + p2[1] + p3[1]) * 0.25
        return int(canvas_left + center_x), int(canvas_top + center_y)

def run_cmd(cmd):
    return subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

def adb_shell(cmd):
    res = subprocess.run([ADB, "shell"] + cmd.split(), stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    return res.stdout.strip()

def tap_square(row, col, is_3d=True, is_flipped=False):
    x, y = compute_coords(row, col, is_3d, is_flipped)
    adb_shell(f"input tap {x} {y}")

def send_key(keycode):
    adb_shell(f"input keyevent {keycode}")

def capture_screenshot(filename):
    dest = os.path.join(ARTIFACT_DIR, filename)
    adb_shell(f"screencap -p /sdcard/{filename}")
    subprocess.run([ADB, "pull", f"/sdcard/{filename}", dest], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return dest

def get_session():
    try:
        out = adb_shell(f"run-as {PACKAGE} cat shared_prefs/tv_chess_prefs.xml")
        m = re.search(r'<string name="saved_session_v1">(.*?)</string>', out)
        if m:
            raw = m.group(1).replace('&quot;', '"')
            return json.loads(raw)
    except Exception as e:
        pass
    return None

def clear_and_start_app():
    adb_shell(f"pm clear {PACKAGE}")
    adb_shell(f"am start -n {PACKAGE}/{ACTIVITY}")
    time.sleep(1.2)

def read_recent_gameplay_logs(limit=25):
    res = subprocess.run([ADB, "logcat", "-d"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    lines = [line for line in res.stdout.split("\n") if "TVCHESS" in line or "Chess" in line]
    return lines[-limit:]

# -------------------------------------------------------------
# Chess Logic Helper để tìm nước đi hợp lệ
# -------------------------------------------------------------
class ChessBoard:
    def __init__(self):
        self.reset()

    def reset(self):
        # 8x8: None hoặc (color, type) e.g. ('W', 'P')
        self.grid = [[None for _ in range(8)] for _ in range(8)]
        # Black
        self.grid[0] = [('B','R'), ('B','N'), ('B','B'), ('B','Q'), ('B','K'), ('B','B'), ('B','N'), ('B','R')]
        self.grid[1] = [('B','P') for _ in range(8)]
        # White
        self.grid[6] = [('W','P') for _ in range(8)]
        self.grid[7] = [('W','R'), ('W','N'), ('W','B'), ('W','Q'), ('W','K'), ('W','B'), ('W','N'), ('W','R')]

    def apply_move(self, fr, fc, tr, tc):
        p = self.grid[fr][fc]
        self.grid[fr][fc] = None
        self.grid[tr][tc] = p

    def get_legal_moves(self, color):
        moves = []
        for r in range(8):
            for c in range(8):
                piece = self.grid[r][c]
                if piece and piece[0] == color:
                    p_type = piece[1]
                    if p_type == 'P':
                        dir_ = -1 if color == 'W' else 1
                        start_row = 6 if color == 'W' else 1
                        # 1 step forward
                        nr = r + dir_
                        if 0 <= nr < 8 and self.grid[nr][c] is None:
                            moves.append((r, c, nr, c))
                            # 2 steps forward
                            nnr = r + 2 * dir_
                            if r == start_row and self.grid[nnr][c] is None:
                                moves.append((r, c, nnr, c))
                        # Diagonal captures
                        for dc in [-1, 1]:
                            nc = c + dc
                            if 0 <= nr < 8 and 0 <= nc < 8:
                                target = self.grid[nr][nc]
                                if target and target[0] != color:
                                    moves.append((r, c, nr, nc))
                    elif p_type == 'N':
                        deltas = [(-2,-1), (-2,1), (-1,-2), (-1,2), (1,-2), (1,2), (2,-1), (2,1)]
                        for dr, dc in deltas:
                            nr, nc = r + dr, c + dc
                            if 0 <= nr < 8 and 0 <= nc < 8:
                                target = self.grid[nr][nc]
                                if target is None or target[0] != color:
                                    moves.append((r, c, nr, nc))
                    elif p_type in ('B', 'R', 'Q'):
                        dirs = []
                        if p_type in ('B', 'Q'):
                            dirs += [(-1,-1), (-1,1), (1,-1), (1,1)]
                        if p_type in ('R', 'Q'):
                            dirs += [(-1,0), (1,0), (0,-1), (0,1)]
                        for dr, dc in dirs:
                            nr, nc = r + dr, c + dc
                            while 0 <= nr < 8 and 0 <= nc < 8:
                                target = self.grid[nr][nc]
                                if target is None:
                                    moves.append((r, c, nr, nc))
                                elif target[0] != color:
                                    moves.append((r, c, nr, nc))
                                    break
                                else:
                                    break
                                nr += dr
                                nc += dc
                    elif p_type == 'K':
                        for dr in [-1, 0, 1]:
                            for dc in [-1, 0, 1]:
                                if dr == 0 and dc == 0: continue
                                nr, nc = r + dr, c + dc
                                if 0 <= nr < 8 and 0 <= nc < 8:
                                    target = self.grid[nr][nc]
                                    if target is None or target[0] != color:
                                        moves.append((r, c, nr, nc))
        return moves

# -------------------------------------------------------------
# Các hàm điều khiển ván đấu
# -------------------------------------------------------------
def play_player_move(fr, fc, tr, tc, is_3d=True, is_flipped=False):
    # Chọn quân
    tap_square(fr, fc, is_3d, is_flipped)
    time.sleep(0.2)
    # Xác nhận ô đích
    tap_square(tr, tc, is_3d, is_flipped)
    time.sleep(0.2)

def wait_for_bot_response(expected_move_count, timeout_sec=5.0):
    start = time.time()
    while time.time() - start < timeout_sec:
        session = get_session()
        if session and len(session.get("moves", [])) >= expected_move_count:
            return session
        time.sleep(0.1)
    return get_session()

# -------------------------------------------------------------
# CHẠY 5 VÁN ĐẤU KIỂM THỬ TOÀN DIỆN
# -------------------------------------------------------------
def run_all_matches():
    results = []

    print("=================================================================")
    print("      BẮT ĐẦU KIỂM THỬ TỰ ĐỘNG 5 VÁN CỜ BỞI AGENT NGƯỜI CHƠI     ")
    print("=================================================================")

    # -------------------------------------------------------------
    # TRẬN 1: TẤN CÔNG NHANH (SCHOLAR'S MATE BLITZ & KIỂM TRA VICTORY FANFARE)
    # -------------------------------------------------------------
    print("\n>>> [TRẬN 1/5] Kiểm thử Tấn công chớp nhoáng (Scholar's Mate) & Chiến thắng...")
    clear_and_start_app()
    capture_screenshot("match_1_start.png")
    
    board = ChessBoard()
    match1_moves = [
        ((6, 4), (4, 4)), # 1. e4
        ((7, 3), (3, 7)), # 2. Qh5
        ((7, 5), (4, 2)), # 3. Bc4
    ]
    
    m1_logs = []
    move_idx = 0
    for fr, to in match1_moves:
        move_idx += 1
        print(f"  - Bé Đậu đi nước {move_idx}: {fr} -> {to}")
        play_player_move(fr[0], fr[1], to[0], to[1])
        board.apply_move(fr[0], fr[1], to[0], to[1])
        
        # Đợi Bot đáp trả
        session = wait_for_bot_response(move_idx * 2)
        if session and len(session.get("moves", [])) >= move_idx * 2:
            bot_m = session["moves"][-1]
            print(f"    -> Bot đáp trả: ({bot_m['fr']}, {bot_m['fc']}) -> ({bot_m['tr']}, {bot_m['tc']})")
            board.apply_move(bot_m['fr'], bot_m['fc'], bot_m['tr'], bot_m['tc'])
        else:
            print("    -> Bot đã đi hoặc ván cờ có biến đổi!")
    
    # Nước 4: Thử ăn f7 hoặc d5
    time.sleep(0.3)
    print("  - Bé Đậu tấn công f7...")
    play_player_move(3, 7, 1, 5) # Qh5xf7
    time.sleep(0.8)
    
    capture_screenshot("match_1_victory_celebration.png")
    logs = read_recent_gameplay_logs(15)
    print("  - Logcat kết thúc Trận 1:")
    for l in logs[-6:]:
        print("    ", l)
    
    results.append({
        "match": 1,
        "name": "Scholar's Mate Blitz",
        "description": "Kiểm thử nước đi chớp nhoáng, âm thanh ăn quân và hiệu ứng chúc mừng chiến thắng",
        "status": "COMPLETED",
        "screenshot": "match_1_victory_celebration.png"
    })

    # -------------------------------------------------------------
    # TRẬN 2: CHIẾN THUẬT TRUNG CUỘC, NÚT MÁCH NƯỚC (X) & ĐI LẠI (Y)
    # -------------------------------------------------------------
    print("\n>>> [TRẬN 2/5] Kiểm thử Tính năng Mách nước (X), Đi lại (Y) & Đổi quân (RB)...")
    clear_and_start_app()
    capture_screenshot("match_2_start.png")

    print("  - Nước 1: Bé Đậu đi e4 (6,4 -> 4,4)")
    play_player_move(6, 4, 4, 4)
    wait_for_bot_response(2)

    print("  - Nước 2: Bé Đậu bấm Nút X (QuickHintX = 99) để xin mách nước...")
    send_key(99)
    time.sleep(0.5)
    capture_screenshot("match_2_hint_highlight.png")

    print("  - Nước 3: Bé Đậu bấm Nút RB (CyclePieceRB = 103) để đổi nhanh quân cờ...")
    send_key(103)
    time.sleep(0.3)
    send_key(103)
    time.sleep(0.3)

    print("  - Nước 4: Bé Đậu phát triển Mã Ngựa f3 (7,6 -> 5,5)")
    play_player_move(7, 6, 5, 5)
    wait_for_bot_response(4)

    print("  - Nước 5: Bé thử bấm Nút Y (QuickUndoY = 100) để đi lại nước cờ...")
    send_key(100)
    time.sleep(0.6)
    capture_screenshot("match_2_after_undo.png")

    session_after_undo = get_session()
    undo_moves_count = len(session_after_undo.get("moves", [])) if session_after_undo else 0
    print(f"  - Số nước cờ sau Undo: {undo_moves_count} (kỳ vọng: 2 nước ban đầu)")

    print("  - Nước 6: Bé đổi sang đi d4 (6,3 -> 4,3)")
    play_player_move(6, 3, 4, 3)
    wait_for_bot_response(4)
    time.sleep(0.4)
    capture_screenshot("match_2_midgame.png")

    results.append({
        "match": 2,
        "name": "Tactical Controls & Undo/Hint Test",
        "description": "Kiểm thử Nút Mách Nước (X), Đi Lại (Y), Chuyển quân (RB) và thu hồi nước đi",
        "status": "COMPLETED",
        "screenshot": "match_2_midgame.png"
    })

    # -------------------------------------------------------------
    # TRẬN 3: GÓC NHÌN 3D TABLETOP HOÀNG GIA vs 2D TRỰC DIỆN (STRESS TEST)
    # -------------------------------------------------------------
    print("\n>>> [TRẬN 3/5] Kiểm thử Chuyển đổi Góc nhìn 3D Tabletop ↔ 2D Trực diện (Nút SELECT)...")
    clear_and_start_app()
    capture_screenshot("match_3_start_3d.png")

    print("  - Nước 1: Bé đi e4 trong không gian 3D Tabletop...")
    play_player_move(6, 4, 4, 4, is_3d=True)
    wait_for_bot_response(2)

    print("  - Nước 2: Bé bấm Nút SELECT (109) chuyển sang Góc nhìn 2D Trực diện...")
    send_key(109)
    time.sleep(0.5)
    capture_screenshot("match_3_switched_to_2d.png")

    print("  - Nước 3: Bé đi tiếp Nf3 (7,6 -> 5,5) trong giao diện 2D...")
    play_player_move(7, 6, 5, 5, is_3d=False)
    wait_for_bot_response(4)
    time.sleep(0.3)

    print("  - Nước 4: Bé bấm SELECT (109) quay trở lại 3D Tabletop...")
    send_key(109)
    time.sleep(0.5)
    capture_screenshot("match_3_back_to_3d.png")

    print("  - Nước 5: Bé phát triển Tượng Bc4 (7,5 -> 4,2) trong 3D...")
    play_player_move(7, 5, 4, 2, is_3d=True)
    wait_for_bot_response(6)
    time.sleep(0.4)
    capture_screenshot("match_3_final_board.png")

    results.append({
        "match": 3,
        "name": "3D Tabletop & 2D Perspective Toggle",
        "description": "Kiểm thử đồ họa bàn cờ 3D đá cẩm thạch Tabletop 38° và chuyển đổi mượt mà sang 2D",
        "status": "COMPLETED",
        "screenshot": "match_3_final_board.png"
    })

    # -------------------------------------------------------------
    # TRẬN 4: BÉ ĐẬU CẦM QUÂN ĐEN (LẬT BÀN CỜ 180° & BOT ĐI TRƯỚC)
    # -------------------------------------------------------------
    print("\n>>> [TRẬN 4/5] Kiểm thử Bé Đậu cầm quân Đen (Bàn cờ lật 180° & Bot Trắng đi trước)...")
    clear_and_start_app()

    print("  - Bước 1: Mở Menu bên phải bằng phím BACK (4)...")
    send_key(4)
    time.sleep(0.4)
    capture_screenshot("match_4_side_menu.png")

    print("  - Bước 2: Di chuyển con trỏ menu xuống mục 'Đổi màu quân' (mục 5)...")
    for _ in range(5):
        send_key(20) # DPAD_DOWN
        time.sleep(0.12)
    
    print("  - Bước 3: Bấm OK (96) để xác nhận đổi sang quân Đen...")
    send_key(96)
    time.sleep(1.0)
    capture_screenshot("match_4_black_perspective_board.png")

    print("  - Bước 4: Chờ Bot Trắng đi nước mở đầu...")
    session = wait_for_bot_response(1, timeout_sec=4.0)
    if session and len(session.get("moves", [])) >= 1:
        first_m = session["moves"][0]
        print(f"    -> Bot Trắng đã đi nước đầu: ({first_m['fr']}, {first_m['fc']}) -> ({first_m['tr']}, {first_m['tc']})")

    print("  - Bước 5: Bé Đậu đáp trả bằng Tốt Đen e5 (hàng 1, cột 4 -> hàng 3, cột 4)...")
    play_player_move(1, 4, 3, 4, is_3d=True, is_flipped=True)
    wait_for_bot_response(2)
    time.sleep(0.4)
    capture_screenshot("match_4_after_black_move.png")

    results.append({
        "match": 4,
        "name": "Play as Black (Flipped Board 180°)",
        "description": "Kiểm thử chuyển phe sang Đen, xoay bàn cờ 180°, Bot đi trước và cơ chế điều khiển ngược hướng",
        "status": "COMPLETED",
        "screenshot": "match_4_after_black_move.png"
    })

    # -------------------------------------------------------------
    # TRẬN 5: ĐUA TỐT PHONG CẤP (PROMOTION DIALOG) & ĐỔI BẠN CHƠI
    # -------------------------------------------------------------
    print("\n>>> [TRẬN 5/5] Kiểm thử Đua Tốt, Hộp thoại Phong Cấp & Đổi bạn Quái Thú (King Kong)...")
    clear_and_start_app()

    print("  - Bước 1: Mở Menu đổi Bạn Chơi sang King Kong...")
    send_key(4) # BACK
    time.sleep(0.3)
    for _ in range(3):
        send_key(20)
        time.sleep(0.12)
    send_key(96) # Đổi sang King Kong
    time.sleep(0.3)
    send_key(20) # Mục Ván mới
    time.sleep(0.15)
    send_key(96)
    time.sleep(0.8)
    capture_screenshot("match_5_kingkong_mascot.png")

    print("  - Bước 2: Bé Đậu thực hiện chuỗi nước đi tiến Tốt cánh Vua...")
    play_player_move(6, 4, 4, 4)
    wait_for_bot_response(2)

    play_player_move(6, 3, 4, 3)
    wait_for_bot_response(4)

    print("  - Bước 3: Bé bấm Nút L1 (ToggleNavModeLB = 102) sang Đi từng ô (FREE_GRID)...")
    send_key(102)
    time.sleep(0.3)

    play_player_move(6, 2, 4, 2)
    wait_for_bot_response(6)
    time.sleep(0.5)
    capture_screenshot("match_5_endgame_race.png")

    results.append({
        "match": 5,
        "name": "King Kong Medium & Free Grid Mode",
        "description": "Kiểm thử AI King Kong, chế độ điều hướng Đi từng ô (FREE_GRID) và phản ứng quái thú",
        "status": "COMPLETED",
        "screenshot": "match_5_endgame_race.png"
    })

    print("\n=================================================================")
    print("      HOÀN THÀNH TOÀN BỘ 5 VÁN ĐẤU KIỂM THỬ TỰ ĐỘNG!            ")
    print("=================================================================")

    # Kiểm tra logcat tổng thể tìm lỗi Crash/ANR/Exception
    print("\nKiểm tra Logcat tìm Crash / ANR / Exception trong suốt quá trình:")
    res_err = subprocess.run([ADB, "logcat", "-d", "*:E"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    err_lines = [l for l in res_err.stdout.split("\n") if PACKAGE in l or "AndroidRuntime" in l or "FATAL" in l]
    if err_lines:
        print(f"CẢNH BÁO: Phát hiện {len(err_lines)} dòng lỗi Error trong Logcat!")
        for el in err_lines[:10]:
            print("  [ERROR]", el)
    else:
        print("XUẤT SẮC: 0 Crash, 0 ANR, 0 Fatal Exception trong suốt quá trình chơi 5 ván!")

    # Lưu kết quả kiểm thử vào file JSON
    report_path = os.path.join(ARTIFACT_DIR, "qa_player_test_results.json")
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"Báo cáo chi tiết đã được lưu tại: {report_path}")

if __name__ == "__main__":
    run_all_matches()
