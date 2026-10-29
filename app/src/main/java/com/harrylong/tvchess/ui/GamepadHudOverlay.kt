package com.harrylong.tvchess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harrylong.tvchess.input.DareuGamepadController
import com.harrylong.tvchess.input.GamepadEventBus
import com.harrylong.tvchess.input.GamepadShortcutHint
import com.harrylong.tvchess.model.BoardNavigationMode

// Bảng màu 4 nút đặc trưng của tay cầm Dareu H101X
private val DareuButtonGreenA = Color(0xFF22C55E)
private val DareuButtonRedB = Color(0xFFEF4444)
private val DareuButtonBlueX = Color(0xFF3B82F6)
private val DareuButtonYellowY = Color(0xFFF59E0B)
private val DareuBumperPurpleLB = Color(0xFF8B5CF6)
private val DareuButtonOrangeSelect = Color(0xFFF97316)

private val HudBarBackground = Color(0xFF0F172A).copy(alpha = 0.92f)
private val HudBarBorder = Color(0xFF334155).copy(alpha = 0.85f)
private val GoldAccent = Color(0xFFFACC15)

/**
 * Thanh HUD hiển thị trạng thái tay cầm Dareu H101X và hướng dẫn nút bấm trực quan
 * cho cơ chế điều khiển 2 pha (Pick & Move) cùng chuyển đổi góc nhìn 3D / 2D.
 */
@Composable
fun DareuGamepadBar(
    navMode: BoardNavigationMode,
    isGamepadConnected: Boolean = GamepadEventBus.isGamepadConnected.collectAsState().value,
    gamepadName: String? = GamepadEventBus.gamepadName.collectAsState().value,
    is3dPerspective: Boolean = true,
    onToggle3dPerspective: (() -> Unit)? = null,
    onToggleNavMode: (() -> Unit)? = null,
    onQuickHint: (() -> Unit)? = null,
    onQuickUndo: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(HudBarBackground)
            .border(1.5.dp, HudBarBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Trạng thái kết nối tay cầm Dareu H101X / Remote TV
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (isGamepadConnected) Color(0xFF064E3B) else Color(0xFF1E293B)
                    )
                    .border(
                        1.5.dp,
                        if (isGamepadConnected) Color(0xFF10B981) else Color(0xFF475569),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🎮",
                    fontSize = 15.sp
                )
            }

            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (isGamepadConnected) Color(0xFF10B981) else Color(0xFF94A3B8)
                            )
                    )
                    Text(
                        text = if (isGamepadConnected) {
                            (gamepadName ?: "Dareu H101X").take(18)
                        } else {
                            "Dareu 101X / Remote"
                        },
                        color = if (isGamepadConnected) Color(0xFF6EE7B7) else Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = if (isGamepadConnected) "Đã kết nối • Sẵn sàng" else "USB / 2.4G / Bluetooth",
                    color = Color(0xFF64748B),
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }

        // 2. Cụm điều khiển ở giữa: Đổi Góc Nhìn 3D (SELECT) & Đổi Kiểu Đi (LB)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Nút Chuyển đổi Góc nhìn: 3D Tabletop ↔ 2D Cổ điển (Nút SELECT)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (is3dPerspective) {
                            Brush.horizontalGradient(
                                listOf(Color(0xFF0369A1).copy(alpha = 0.85f), Color(0xFF0284C7).copy(alpha = 0.85f))
                            )
                        } else {
                            Brush.horizontalGradient(
                                listOf(Color(0xFF334155).copy(alpha = 0.85f), Color(0xFF475569).copy(alpha = 0.85f))
                            )
                        }
                    )
                    .border(
                        1.5.dp,
                        if (is3dPerspective) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onToggle3dPerspective?.invoke() }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = if (is3dPerspective) "🎲 3D Tabletop" else "📐 2D Bàn Cờ",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "SELECT",
                            color = DareuButtonOrangeSelect,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Nút Chuyển đổi Kiểu di chuyển: Nhảy thông minh ⚡ ↔ Đi từng ô 🎯 (Nút LB/L1)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (navMode == BoardNavigationMode.SMART_JUMP) {
                            Brush.horizontalGradient(
                                listOf(Color(0xFF4C1D95).copy(alpha = 0.85f), Color(0xFF312E81).copy(alpha = 0.85f))
                            )
                        } else {
                            Brush.horizontalGradient(
                                listOf(Color(0xFF065F46).copy(alpha = 0.85f), Color(0xFF047857).copy(alpha = 0.85f))
                            )
                        }
                    )
                    .border(
                        1.5.dp,
                        if (navMode == BoardNavigationMode.SMART_JUMP) Color(0xFFA78BFA) else Color(0xFF34D399),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onToggleNavMode?.invoke() }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "${navMode.badgeEmoji} ${navMode.vietnameseTitle}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "LB",
                            color = GoldAccent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 3. Cụm nút bấm chuẩn của Dareu H101X cho cơ chế Pick & Move (Chọn & Đánh cờ)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            // Nút A (Xanh lá) - Chọn quân / Đánh cờ
            GamepadButtonBadge(
                buttonLabel = "A",
                buttonColor = DareuButtonGreenA,
                actionName = "Chọn / Đi"
            )

            // Nút B (Đỏ) - Hủy chọn
            GamepadButtonBadge(
                buttonLabel = "B",
                buttonColor = DareuButtonRedB,
                actionName = "Hủy chọn"
            )

            // Nút X (Xanh dương) - Mách nước
            GamepadButtonBadge(
                buttonLabel = "X",
                buttonColor = DareuButtonBlueX,
                actionName = "Mách nước",
                onClick = onQuickHint
            )

            // Nút Y (Vàng cam) - Đi lại
            GamepadButtonBadge(
                buttonLabel = "Y",
                buttonColor = DareuButtonYellowY,
                actionName = "Đi lại",
                onClick = onQuickUndo
            )
        }
    }
}

/**
 * Biểu tượng nút bấm tròn với màu sắc đặc trưng của tay cầm Dareu H101X.
 */
@Composable
private fun GamepadButtonBadge(
    buttonLabel: String,
    buttonColor: Color,
    actionName: String,
    modifier: Modifier = Modifier,
    isPill: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val clickableMod = if (onClick != null) {
        Modifier.clickable { onClick() }
    } else Modifier

    Row(
        modifier = modifier.then(clickableMod),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = if (isPill) {
                Modifier
                    .height(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(buttonColor)
                    .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp)
            } else {
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(buttonColor)
                    .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
            },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = buttonLabel,
                color = Color.White,
                fontSize = if (isPill) 9.sp else 10.sp,
                fontWeight = FontWeight.Black
            )
        }

        Text(
            text = actionName,
            color = Color(0xFFE2E8F0),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Hộp thoại hiển thị đầy đủ sơ đồ phím tay cầm Dareu H101X và cơ chế Pick & Move 2 pha cho bé Đậu.
 */
@Composable
fun DareuGamepadGuideDialog(
    onDismiss: () -> Unit
) {
    val shortcuts = listOf(
        GamepadShortcutHint("A", "Chọn / Đánh cờ", 0xFF22C55EL, "🟢"),
        GamepadShortcutHint("B", "Hủy chọn / Quay lại", 0xFFEF4444L, "🔴"),
        GamepadShortcutHint("X", "Mách nước nhanh", 0xFF3B82F6L, "💡"),
        GamepadShortcutHint("Y", "Đi lại nước vừa rồi", 0xFFF59E0BL, "↩️"),
        GamepadShortcutHint("SELECT", "Đổi góc nhìn (3D ↔ 2D)", 0xFFF97316L, "🎲"),
        GamepadShortcutHint("LB", "Kiểu đi (Tự do 🎯 / Nhảy ⚡)", 0xFF8B5CF6L, "⚡"),
        GamepadShortcutHint("RB", "Quân cờ kế tiếp", 0xFFEC4899L, "⏭️"),
        GamepadShortcutHint("LT/RT", "Đọc tên quân cờ", 0xFF14B8A6L, "🔊"),
        GamepadShortcutHint("START", "Mở / Đóng Menu", 0xFF6366F1L, "📋")
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.98f))
            .border(2.5.dp, GoldAccent, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Tiêu đề & Nút đóng
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "🎮", fontSize = 28.sp)
                    Column {
                        Text(
                            text = "HƯỚNG DẪN TAY CẦM DAREU H101X & REMOTE TV",
                            color = GoldAccent,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Hỗ trợ kết nối có dây USB, Receiver 2.4G và Bluetooth không dây",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF334155))
                        .clickable { onDismiss() }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Đóng (B)",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Banner hướng dẫn chi tiết cơ chế đi cờ 2 pha (Pick & Move)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E3A2F))
                    .border(1.5.dp, Color(0xFF22C55E), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "🎯 CƠ CHẾ ĐI CỜ 2 PHA (PICK & MOVE) DỄ DÀNG CHO BÉ ĐẬU:",
                        color = Color(0xFF86EFAC),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = "🟢", fontSize = 12.sp)
                        Text(
                            text = "Bước 1: Trỏ tới quân cờ bấm A để nhấc quân (hiển thị khung viền đỏ 3D ôm sát quân).",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = "🟢", fontSize = 12.sp)
                        Text(
                            text = "Bước 2: Di chuyển tới ô viền xanh lá bấm A để đánh cờ (hoặc bấm B để hủy chọn).",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Lưới các phím tắt trực quan
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                shortcuts.chunked(3).forEach { rowHints ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowHints.forEach { hint ->
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1E293B))
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(hint.colorHex))
                                        .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = hint.buttonLabel.take(2),
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }

                                Column {
                                    Text(
                                        text = "${hint.iconEmoji} ${hint.actionTitle}",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Ghi chú điều hướng cần Analog và D-Pad
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E3A8A).copy(alpha = 0.4f))
                    .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = "🕹️", fontSize = 20.sp)
                Text(
                    text = "D-Pad / Cần Trái: Di chuyển theo chế độ • Cần Phải: Di chuyển tự do từng ô bất kỳ lúc nào • Cò L2/R2: Nghe đọc tên và cách đi quân cờ",
                    color = Color(0xFFBFDBFE),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
