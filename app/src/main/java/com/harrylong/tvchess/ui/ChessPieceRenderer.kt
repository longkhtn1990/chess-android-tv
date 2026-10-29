package com.harrylong.tvchess.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.harrylong.tvchess.model.Piece
import com.harrylong.tvchess.model.PieceColor
import com.harrylong.tvchess.model.PieceType

/**
 * Bộ dựng hình Quân Cờ 3D-Vector Hoàng Gia đẳng cấp 4K dành cho "Đậu Học Cờ Vua".
 * - Tối ưu 60fps trên GPU Android TV qua Jetpack Compose DrawScope.
 * - Hiệu ứng ánh sáng khối (Specular Highlights), bóng đổ đa lớp tách rời theo cao độ (Elevation).
 * - Chi tiết tinh tế: Khiên Hiệp Sĩ cho Tốt, Cửa vòm cho Xe, Mắt ngọc có hồn cho Ngựa, Thánh giá đính Ruby cho Vua.
 */
object ChessPieceRenderer {

    data class PiecePalette(
        val fillBrush: Brush,
        val highlightBrush: Brush,
        val baseColor: Color,
        val innerAccent: Color,
        val strokeColor: Color,
        val specularColor: Color,
        val goldAccent: Color,
        val rubyColor: Color = Color(0xFFEF4444),
        val sapphireColor: Color = Color(0xFF38BDF8),
        val emeraldColor: Color = Color(0xFF10B981)
    )

    fun DrawScope.drawChessPiece(
        piece: Piece,
        topLeft: Offset,
        squareSize: Float,
        isSelected: Boolean = false,
        scale: Float = 1f,
        elevationProgress: Float = if (isSelected) 1f else 0f
    ) {
        val s = squareSize
        val isWhite = piece.color == PieceColor.WHITE

        // 1. TÍNH TOÁN CAO ĐỘ (ELEVATION) VÀ ĐỔ BÓNG ĐA LỚP TỰ NHIÊN
        // Khi quân cờ được nhấc lên (khi chọn hoặc khi đang nhảy Parabol), bóng đổ nằm lại mặt bàn và mờ rộng dần
        val liftOffset = -s * (0.045f * (if (isSelected) 1f else 0f) + 0.14f * elevationProgress)
        val shadowW = s * (0.64f - 0.10f * elevationProgress).coerceAtLeast(0.35f * s)
        val shadowH = s * (0.15f - 0.03f * elevationProgress).coerceAtLeast(0.08f * s)
        val shadowAlpha = ((if (isSelected) 0.50f else 0.32f) * (1f - 0.35f * elevationProgress)).coerceIn(0.08f, 0.55f)
        val shadowLeft = topLeft.x + (s - shadowW) / 2f
        val shadowTop = topLeft.y + s * (0.78f + 0.03f * elevationProgress)

        // Lớp bóng chân đế
        drawCircle(
            color = Color.Black.copy(alpha = shadowAlpha * 0.35f),
            radius = s * 0.28f,
            center = Offset(topLeft.x + s * 0.5f, topLeft.y + s * 0.84f)
        )

        // 2. TẠO BẢNG MÀU GRADIENT 3D CHO TỪNG PHE
        val ox = topLeft.x
        val oy = topLeft.y + liftOffset

        val palette = if (isWhite) {
            // Phe Trắng: Cẩm thạch ngọc ngà Hoàng Gia, bóng viền nâu trầm sang trọng
            PiecePalette(
                fillBrush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFFBF8F2),
                        Color(0xFFEFE8DB),
                        Color(0xFFDED3BF)
                    ),
                    start = Offset(ox + s * 0.25f, oy + s * 0.15f),
                    end = Offset(ox + s * 0.75f, oy + s * 0.85f)
                ),
                highlightBrush = Brush.linearGradient(
                    colors = listOf(Color(0x99FFFFFF), Color(0x00FFFFFF)),
                    start = Offset(ox + s * 0.30f, oy + s * 0.20f),
                    end = Offset(ox + s * 0.55f, oy + s * 0.60f)
                ),
                baseColor = Color(0xFFF9F6F0),
                innerAccent = Color(0xFFD2C4AE),
                strokeColor = Color(0xFF422818),
                specularColor = Color(0xFFFFFFFF),
                goldAccent = Color(0xFFD97706)
            )
        } else {
            // Phe Đen: Hắc Diệu Thạch bóng bẩy huyền bí, viền mạ vàng Hoàng Gia tương phản tuyệt hảo trên TV 4K
            PiecePalette(
                fillBrush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF3B4454),
                        Color(0xFF232B38),
                        Color(0xFF141923),
                        Color(0xFF0C1018)
                    ),
                    start = Offset(ox + s * 0.25f, oy + s * 0.15f),
                    end = Offset(ox + s * 0.75f, oy + s * 0.85f)
                ),
                highlightBrush = Brush.linearGradient(
                    colors = listOf(Color(0x6664748B), Color(0x000F172A)),
                    start = Offset(ox + s * 0.30f, oy + s * 0.20f),
                    end = Offset(ox + s * 0.55f, oy + s * 0.60f)
                ),
                baseColor = Color(0xFF1F2937),
                innerAccent = Color(0xFF475569),
                strokeColor = Color(0xFFF59E0B), // Viền vàng kim rực rỡ
                specularColor = Color(0xFF94A3B8),
                goldAccent = Color(0xFFFDE047)
            )
        }

        val strokeWidth = (s * 0.038f).coerceAtLeast(2.2f)
        val strokeStyle = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        // 3. VẼ QUÂN CỜ KÈM HỖ TRỢ PHÓNG TO SCALE MƯỢT 60FPS
        if (scale != 1f) {
            withTransform({
                scale(scale, scale, pivot = Offset(ox + s * 0.5f, oy + s * 0.5f))
            }) {
                renderPieceShape(piece.type, ox, oy, s, palette, strokeStyle)
            }
        } else {
            renderPieceShape(piece.type, ox, oy, s, palette, strokeStyle)
        }
    }

    private fun DrawScope.renderPieceShape(
        type: PieceType,
        ox: Float,
        oy: Float,
        s: Float,
        palette: PiecePalette,
        strokeStyle: Stroke
    ) {
        when (type) {
            PieceType.PAWN -> drawPawn3D(ox, oy, s, palette, strokeStyle)
            PieceType.ROOK -> drawRook3D(ox, oy, s, palette, strokeStyle)
            PieceType.KNIGHT -> drawKnight3D(ox, oy, s, palette, strokeStyle)
            PieceType.BISHOP -> drawBishop3D(ox, oy, s, palette, strokeStyle)
            PieceType.QUEEN -> drawQueen3D(ox, oy, s, palette, strokeStyle)
            PieceType.KING -> drawKing3D(ox, oy, s, palette, strokeStyle)
        }
    }

    // --- BỆ CHÂN ĐẾ 3D HOÀNG GIA DÙNG CHUNG CHO TẤT CẢ QUÂN CỜ ---
    private fun DrawScope.drawBasePedestal3D(
        ox: Float,
        oy: Float,
        s: Float,
        palette: PiecePalette,
        strokeStyle: Stroke
    ) {
        // Tầng bệ đáy lớn
        val baseTier1 = Path().apply {
            moveTo(ox + s * 0.18f, oy + s * 0.85f)
            lineTo(ox + s * 0.82f, oy + s * 0.85f)
            lineTo(ox + s * 0.77f, oy + s * 0.79f)
            lineTo(ox + s * 0.23f, oy + s * 0.79f)
            close()
        }
        drawPath(baseTier1, brush = palette.fillBrush, style = Fill)
        drawPath(baseTier1, color = palette.strokeColor, style = strokeStyle)

        // Tầng bệ thắt thứ hai
        val baseTier2 = Path().apply {
            moveTo(ox + s * 0.24f, oy + s * 0.79f)
            lineTo(ox + s * 0.76f, oy + s * 0.79f)
            lineTo(ox + s * 0.71f, oy + s * 0.74f)
            lineTo(ox + s * 0.29f, oy + s * 0.74f)
            close()
        }
        drawPath(baseTier2, color = palette.innerAccent, style = Fill)
        drawPath(baseTier2, color = palette.strokeColor, style = strokeStyle)

        // Vòng khuyên mạ vàng tạo điểm nhấn quý phái
        drawLine(
            color = palette.goldAccent,
            start = Offset(ox + s * 0.25f, oy + s * 0.81f),
            end = Offset(ox + s * 0.75f, oy + s * 0.81f),
            strokeWidth = strokeStyle.width * 0.8f,
            cap = StrokeCap.Round
        )

        // Vệt phản chiếu ánh sáng chân đế (Specular highlight)
        drawLine(
            color = palette.specularColor.copy(alpha = 0.45f),
            start = Offset(ox + s * 0.28f, oy + s * 0.76f),
            end = Offset(ox + s * 0.48f, oy + s * 0.76f),
            strokeWidth = strokeStyle.width * 0.6f,
            cap = StrokeCap.Round
        )
    }

    // --- 1. LÍNH TỐT (PAWN): KHIÊN DŨNG SĨ VÀ ĐẦU KHỐI CẦU 3D ---
    private fun DrawScope.drawPawn3D(
        ox: Float,
        oy: Float,
        s: Float,
        palette: PiecePalette,
        strokeStyle: Stroke
    ) {
        // Thân áo giáp Lính Tốt
        val bodyPath = Path().apply {
            moveTo(ox + s * 0.28f, oy + s * 0.74f)
            cubicTo(
                ox + s * 0.30f, oy + s * 0.58f,
                ox + s * 0.38f, oy + s * 0.50f,
                ox + s * 0.41f, oy + s * 0.44f
            )
            lineTo(ox + s * 0.59f, oy + s * 0.44f)
            cubicTo(
                ox + s * 0.62f, oy + s * 0.50f,
                ox + s * 0.70f, oy + s * 0.58f,
                ox + s * 0.72f, oy + s * 0.74f
            )
            close()
        }
        drawPath(bodyPath, brush = palette.fillBrush, style = Fill)
        drawPath(bodyPath, color = palette.strokeColor, style = strokeStyle)

        // Vệt bóng khối 3D bên sườn
        drawPath(bodyPath, brush = palette.highlightBrush, style = Fill)

        // Cổ áo lính tốt mạ vàng
        drawRoundRect(
            color = palette.goldAccent,
            topLeft = Offset(ox + s * 0.36f, oy + s * 0.41f),
            size = Size(s * 0.28f, s * 0.045f),
            style = Fill
        )
        drawRoundRect(
            color = palette.strokeColor,
            topLeft = Offset(ox + s * 0.36f, oy + s * 0.41f),
            size = Size(s * 0.28f, s * 0.045f),
            style = strokeStyle
        )

        // Huy hiệu Khiên Dũng Sĩ (Mini Shield) trên ngực Lính Tốt
        val shieldPath = Path().apply {
            moveTo(ox + s * 0.44f, oy + s * 0.52f)
            lineTo(ox + s * 0.56f, oy + s * 0.52f)
            lineTo(ox + s * 0.56f, oy + s * 0.61f)
            cubicTo(
                ox + s * 0.56f, oy + s * 0.67f,
                ox + s * 0.50f, oy + s * 0.70f,
                ox + s * 0.50f, oy + s * 0.70f
            )
            cubicTo(
                ox + s * 0.50f, oy + s * 0.70f,
                ox + s * 0.44f, oy + s * 0.67f,
                ox + s * 0.44f, oy + s * 0.61f
            )
            close()
        }
        drawPath(shieldPath, color = palette.goldAccent, style = Fill)
        drawPath(shieldPath, color = palette.strokeColor, style = Stroke(strokeStyle.width * 0.6f))
        // Điểm ngọc dũng sĩ ở tâm khiên
        drawCircle(
            color = palette.rubyColor,
            radius = s * 0.022f,
            center = Offset(ox + s * 0.50f, oy + s * 0.59f)
        )

        // Đầu tròn khối cầu 3D với Radial Gradient
        val headCenter = Offset(ox + s * 0.50f, oy + s * 0.27f)
        val headRadius = s * 0.145f
        val headBrush = Brush.radialGradient(
            colors = listOf(
                palette.specularColor,
                palette.baseColor,
                palette.innerAccent
            ),
            center = Offset(headCenter.x - headRadius * 0.35f, headCenter.y - headRadius * 0.35f),
            radius = headRadius * 1.35f
        )
        drawCircle(brush = headBrush, radius = headRadius, center = headCenter)
        drawCircle(color = palette.strokeColor, radius = headRadius, center = headCenter, style = strokeStyle)

        // Chấm phản chiếu ánh sáng lấp lánh trên đầu tròn
        drawCircle(
            color = palette.specularColor.copy(alpha = 0.85f),
            radius = s * 0.035f,
            center = Offset(headCenter.x - headRadius * 0.35f, headCenter.y - headRadius * 0.35f)
        )

        drawBasePedestal3D(ox, oy, s, palette, strokeStyle)
    }

    // --- 2. XE PHÁO ĐÀI (ROOK): LỖ CHÂU MAI & CỬA SỔ VÒM HIỆP SĨ ---
    private fun DrawScope.drawRook3D(
        ox: Float,
        oy: Float,
        s: Float,
        palette: PiecePalette,
        strokeStyle: Stroke
    ) {
        val towerPath = Path().apply {
            moveTo(ox + s * 0.28f, oy + s * 0.74f)
            lineTo(ox + s * 0.32f, oy + s * 0.38f)
            lineTo(ox + s * 0.25f, oy + s * 0.34f)
            lineTo(ox + s * 0.25f, oy + s * 0.20f)
            // Lỗ châu mai 1
            lineTo(ox + s * 0.35f, oy + s * 0.20f)
            lineTo(ox + s * 0.35f, oy + s * 0.27f)
            lineTo(ox + s * 0.44f, oy + s * 0.27f)
            // Lỗ châu mai 2
            lineTo(ox + s * 0.44f, oy + s * 0.20f)
            lineTo(ox + s * 0.56f, oy + s * 0.20f)
            lineTo(ox + s * 0.56f, oy + s * 0.27f)
            lineTo(ox + s * 0.65f, oy + s * 0.27f)
            // Lỗ châu mai 3
            lineTo(ox + s * 0.65f, oy + s * 0.20f)
            lineTo(ox + s * 0.75f, oy + s * 0.20f)
            lineTo(ox + s * 0.75f, oy + s * 0.34f)
            lineTo(ox + s * 0.68f, oy + s * 0.38f)
            lineTo(ox + s * 0.72f, oy + s * 0.74f)
            close()
        }
        drawPath(towerPath, brush = palette.fillBrush, style = Fill)
        drawPath(towerPath, color = palette.strokeColor, style = strokeStyle)
        drawPath(towerPath, brush = palette.highlightBrush, style = Fill)

        // Gờ tường đá thành trì mạ vàng
        val wallRim = Path().apply {
            moveTo(ox + s * 0.25f, oy + s * 0.34f)
            lineTo(ox + s * 0.75f, oy + s * 0.34f)
            lineTo(ox + s * 0.68f, oy + s * 0.38f)
            lineTo(ox + s * 0.32f, oy + s * 0.38f)
            close()
        }
        drawPath(wallRim, color = palette.goldAccent.copy(alpha = 0.5f), style = Fill)
        drawPath(wallRim, color = palette.strokeColor, style = strokeStyle)

        // Cửa Sổ Vòm Hiệp Sĩ (Arch Window) giữa thân Xe
        val windowPath = Path().apply {
            moveTo(ox + s * 0.44f, oy + s * 0.62f)
            lineTo(ox + s * 0.44f, oy + s * 0.48f)
            cubicTo(
                ox + s * 0.44f, oy + s * 0.43f,
                ox + s * 0.56f, oy + s * 0.43f,
                ox + s * 0.56f, oy + s * 0.48f
            )
            lineTo(ox + s * 0.56f, oy + s * 0.62f)
            close()
        }
        drawPath(windowPath, color = palette.innerAccent, style = Fill)
        drawPath(windowPath, color = palette.strokeColor, style = Stroke(strokeStyle.width * 0.8f))
        // Ánh đèn ấm phát ra từ trong tháp
        drawCircle(
            color = palette.goldAccent,
            radius = s * 0.025f,
            center = Offset(ox + s * 0.50f, oy + s * 0.52f)
        )

        drawBasePedestal3D(ox, oy, s, palette, strokeStyle)
    }

    // --- 3. NGỰA CHIẾN (KNIGHT): BỜM UỐN LƯỢN & MẮT NGỌC TINH ANH ---
    private fun DrawScope.drawKnight3D(
        ox: Float,
        oy: Float,
        s: Float,
        palette: PiecePalette,
        strokeStyle: Stroke
    ) {
        val horsePath = Path().apply {
            moveTo(ox + s * 0.28f, oy + s * 0.74f)
            cubicTo(
                ox + s * 0.29f, oy + s * 0.56f,
                ox + s * 0.38f, oy + s * 0.48f,
                ox + s * 0.42f, oy + s * 0.42f
            )
            // Mõm ngựa
            lineTo(ox + s * 0.25f, oy + s * 0.46f)
            lineTo(ox + s * 0.22f, oy + s * 0.38f)
            cubicTo(
                ox + s * 0.28f, oy + s * 0.28f,
                ox + s * 0.38f, oy + s * 0.22f,
                ox + s * 0.46f, oy + s * 0.16f
            )
            // Tai ngựa dũng cảm
            lineTo(ox + s * 0.52f, oy + s * 0.23f)
            lineTo(ox + s * 0.58f, oy + s * 0.17f)
            // Bờm ngựa lượn sóng
            cubicTo(
                ox + s * 0.73f, oy + s * 0.26f,
                ox + s * 0.77f, oy + s * 0.50f,
                ox + s * 0.72f, oy + s * 0.74f
            )
            close()
        }
        drawPath(horsePath, brush = palette.fillBrush, style = Fill)
        drawPath(horsePath, color = palette.strokeColor, style = strokeStyle)
        drawPath(horsePath, brush = palette.highlightBrush, style = Fill)

        // Các lọn bờm ngựa 3D
        val mane1 = Path().apply {
            moveTo(ox + s * 0.58f, oy + s * 0.25f)
            cubicTo(
                ox + s * 0.67f, oy + s * 0.28f,
                ox + s * 0.71f, oy + s * 0.36f,
                ox + s * 0.68f, oy + s * 0.42f
            )
        }
        val mane2 = Path().apply {
            moveTo(ox + s * 0.64f, oy + s * 0.42f)
            cubicTo(
                ox + s * 0.73f, oy + s * 0.46f,
                ox + s * 0.75f, oy + s * 0.56f,
                ox + s * 0.71f, oy + s * 0.64f
            )
        }
        drawPath(mane1, color = palette.goldAccent, style = Stroke(strokeStyle.width * 0.9f, cap = StrokeCap.Round))
        drawPath(mane2, color = palette.goldAccent, style = Stroke(strokeStyle.width * 0.9f, cap = StrokeCap.Round))

        // Dây cương mõm ngựa mạ vàng
        val bridlePath = Path().apply {
            moveTo(ox + s * 0.27f, oy + s * 0.44f)
            lineTo(ox + s * 0.38f, oy + s * 0.36f)
            lineTo(ox + s * 0.46f, oy + s * 0.42f)
        }
        drawPath(bridlePath, color = palette.goldAccent, style = Stroke(strokeStyle.width * 0.75f, cap = StrokeCap.Round))

        // Mắt ngựa tinh anh lấp lánh (3 lớp: viền, ngọc xanh, điểm sáng phản chiếu)
        val eyeCenter = Offset(ox + s * 0.37f, oy + s * 0.30f)
        drawCircle(color = palette.strokeColor, radius = s * 0.038f, center = eyeCenter)
        drawCircle(color = palette.sapphireColor, radius = s * 0.026f, center = eyeCenter)
        drawCircle(color = Color.White, radius = s * 0.012f, center = Offset(eyeCenter.x - s * 0.008f, eyeCenter.y - s * 0.008f))

        // Lỗ mũi ngựa dũng cảm
        drawCircle(color = palette.strokeColor, radius = s * 0.015f, center = Offset(ox + s * 0.25f, oy + s * 0.41f))

        drawBasePedestal3D(ox, oy, s, palette, strokeStyle)
    }

    // --- 4. QUÂN TƯỢNG (BISHOP): MŨ GIÁO MỤC & CHỮ THẬP THÁNH GIÁ ---
    private fun DrawScope.drawBishop3D(
        ox: Float,
        oy: Float,
        s: Float,
        palette: PiecePalette,
        strokeStyle: Stroke
    ) {
        val mitrePath = Path().apply {
            moveTo(ox + s * 0.28f, oy + s * 0.74f)
            cubicTo(
                ox + s * 0.31f, oy + s * 0.60f,
                ox + s * 0.36f, oy + s * 0.52f,
                ox + s * 0.36f, oy + s * 0.46f
            )
            cubicTo(
                ox + s * 0.27f, oy + s * 0.36f,
                ox + s * 0.35f, oy + s * 0.21f,
                ox + s * 0.50f, oy + s * 0.19f
            )
            cubicTo(
                ox + s * 0.65f, oy + s * 0.21f,
                ox + s * 0.73f, oy + s * 0.36f,
                ox + s * 0.64f, oy + s * 0.46f
            )
            cubicTo(
                ox + s * 0.64f, oy + s * 0.52f,
                ox + s * 0.69f, oy + s * 0.60f,
                ox + s * 0.72f, oy + s * 0.74f
            )
            close()
        }
        drawPath(mitrePath, brush = palette.fillBrush, style = Fill)
        drawPath(mitrePath, color = palette.strokeColor, style = strokeStyle)
        drawPath(mitrePath, brush = palette.highlightBrush, style = Fill)

        // Quả cầu ngọc hoàng gia trên đỉnh mũ
        val sphereCenter = Offset(ox + s * 0.50f, oy + s * 0.14f)
        val sphereRadius = s * 0.045f
        drawCircle(color = palette.goldAccent, radius = sphereRadius, center = sphereCenter)
        drawCircle(color = palette.strokeColor, radius = sphereRadius, center = sphereCenter, style = strokeStyle)
        drawCircle(color = Color.White, radius = sphereRadius * 0.35f, center = Offset(sphereCenter.x - sphereRadius * 0.3f, sphereCenter.y - sphereRadius * 0.3f))

        // Vết khía chéo đặc trưng trên mũ Tượng
        val cutSlit = Path().apply {
            moveTo(ox + s * 0.54f, oy + s * 0.22f)
            lineTo(ox + s * 0.64f, oy + s * 0.32f)
        }
        drawPath(cutSlit, color = palette.strokeColor, style = Stroke(strokeStyle.width * 1.1f, cap = StrokeCap.Round))

        // Huy hiệu Chữ Thập Thánh Giá nổi bật giữa ngực Tượng
        val crossH = Path().apply {
            moveTo(ox + s * 0.42f, oy + s * 0.38f)
            lineTo(ox + s * 0.58f, oy + s * 0.38f)
        }
        val crossV = Path().apply {
            moveTo(ox + s * 0.50f, oy + s * 0.31f)
            lineTo(ox + s * 0.50f, oy + s * 0.45f)
        }
        drawPath(crossH, color = palette.goldAccent, style = Stroke(strokeStyle.width * 1.1f, cap = StrokeCap.Round))
        drawPath(crossV, color = palette.goldAccent, style = Stroke(strokeStyle.width * 1.1f, cap = StrokeCap.Round))
        // Viên ngọc xanh ở giao điểm thánh giá
        drawCircle(color = palette.emeraldColor, radius = s * 0.018f, center = Offset(ox + s * 0.50f, oy + s * 0.38f))

        drawBasePedestal3D(ox, oy, s, palette, strokeStyle)
    }

    // --- 5. NỮ HOÀNG (QUEEN): VƯƠNG MIỆN 5 ĐỈNH ĐÍNH NGỌC LỘNG LẪY ---
    private fun DrawScope.drawQueen3D(
        ox: Float,
        oy: Float,
        s: Float,
        palette: PiecePalette,
        strokeStyle: Stroke
    ) {
        val crownPath = Path().apply {
            moveTo(ox + s * 0.25f, oy + s * 0.74f)
            lineTo(ox + s * 0.16f, oy + s * 0.31f)
            lineTo(ox + s * 0.33f, oy + s * 0.48f)
            lineTo(ox + s * 0.36f, oy + s * 0.24f)
            lineTo(ox + s * 0.50f, oy + s * 0.45f)
            lineTo(ox + s * 0.64f, oy + s * 0.24f)
            lineTo(ox + s * 0.67f, oy + s * 0.48f)
            lineTo(ox + s * 0.84f, oy + s * 0.31f)
            lineTo(ox + s * 0.75f, oy + s * 0.74f)
            close()
        }
        drawPath(crownPath, brush = palette.fillBrush, style = Fill)
        drawPath(crownPath, color = palette.strokeColor, style = strokeStyle)
        drawPath(crownPath, brush = palette.highlightBrush, style = Fill)

        // Chóp nhọn chính giữa vương miện
        val centerSpike = Path().apply {
            moveTo(ox + s * 0.44f, oy + s * 0.45f)
            lineTo(ox + s * 0.50f, oy + s * 0.19f)
            lineTo(ox + s * 0.56f, oy + s * 0.45f)
            close()
        }
        drawPath(centerSpike, color = palette.goldAccent, style = Fill)
        drawPath(centerSpike, color = palette.strokeColor, style = strokeStyle)

        // 5 Viên ngọc quý lấp lánh trên 5 đỉnh vương miện Nữ Hoàng
        val gems = listOf(
            Triple(Offset(ox + s * 0.16f, oy + s * 0.26f), s * 0.038f, palette.rubyColor),
            Triple(Offset(ox + s * 0.36f, oy + s * 0.20f), s * 0.040f, palette.sapphireColor),
            Triple(Offset(ox + s * 0.50f, oy + s * 0.15f), s * 0.048f, palette.goldAccent), // Ngọc trung tâm lớn nhất
            Triple(Offset(ox + s * 0.64f, oy + s * 0.20f), s * 0.040f, palette.sapphireColor),
            Triple(Offset(ox + s * 0.84f, oy + s * 0.26f), s * 0.038f, palette.rubyColor)
        )
        for ((pt, radius, gemColor) in gems) {
            drawCircle(color = gemColor, radius = radius, center = pt)
            drawCircle(color = palette.strokeColor, radius = radius, center = pt, style = strokeStyle)
            // Điểm sáng specular trên viên ngọc
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = radius * 0.35f,
                center = Offset(pt.x - radius * 0.3f, pt.y - radius * 0.3f)
            )
        }

        // Đai thắt lưng đính ngọc của Nữ Hoàng
        drawArc(
            color = palette.goldAccent,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(ox + s * 0.28f, oy + s * 0.52f),
            size = Size(s * 0.44f, s * 0.16f),
            style = Stroke(width = strokeStyle.width * 1.2f, cap = StrokeCap.Round)
        )
        // Hạt ngọc lớn giữa đai lưng
        drawCircle(color = palette.rubyColor, radius = s * 0.032f, center = Offset(ox + s * 0.50f, oy + s * 0.54f))
        drawCircle(color = palette.strokeColor, radius = s * 0.032f, center = Offset(ox + s * 0.50f, oy + s * 0.54f), style = strokeStyle)

        drawBasePedestal3D(ox, oy, s, palette, strokeStyle)
    }

    // --- 6. ĐỨC VUA (KING): THÁNH GIÁ ĐÍNH RUBY & BỘ TAM NGỌC HOÀNG GIA ---
    private fun DrawScope.drawKing3D(
        ox: Float,
        oy: Float,
        s: Float,
        palette: PiecePalette,
        strokeStyle: Stroke
    ) {
        // Thân áo choàng Đức Vua bệ vệ uy nghiêm
        val kingBody = Path().apply {
            moveTo(ox + s * 0.25f, oy + s * 0.74f)
            lineTo(ox + s * 0.20f, oy + s * 0.40f)
            cubicTo(
                ox + s * 0.20f, oy + s * 0.26f,
                ox + s * 0.40f, oy + s * 0.26f,
                ox + s * 0.50f, oy + s * 0.36f
            )
            cubicTo(
                ox + s * 0.60f, oy + s * 0.26f,
                ox + s * 0.80f, oy + s * 0.26f,
                ox + s * 0.80f, oy + s * 0.40f
            )
            lineTo(ox + s * 0.75f, oy + s * 0.74f)
            close()
        }
        drawPath(kingBody, brush = palette.fillBrush, style = Fill)
        drawPath(kingBody, color = palette.strokeColor, style = strokeStyle)
        drawPath(kingBody, brush = palette.highlightBrush, style = Fill)

        // Thánh Giá Hoàng Gia lộng lẫy trên đỉnh vương miện Vua
        val crossStroke = Stroke(width = strokeStyle.width * 1.35f, cap = StrokeCap.Round)
        drawLine(
            color = palette.goldAccent,
            start = Offset(ox + s * 0.50f, oy + s * 0.07f),
            end = Offset(ox + s * 0.50f, oy + s * 0.25f),
            strokeWidth = crossStroke.width,
            cap = StrokeCap.Round
        )
        drawLine(
            color = palette.strokeColor,
            start = Offset(ox + s * 0.50f, oy + s * 0.07f),
            end = Offset(ox + s * 0.50f, oy + s * 0.25f),
            strokeWidth = crossStroke.width * 0.5f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = palette.goldAccent,
            start = Offset(ox + s * 0.40f, oy + s * 0.14f),
            end = Offset(ox + s * 0.60f, oy + s * 0.14f),
            strokeWidth = crossStroke.width,
            cap = StrokeCap.Round
        )
        drawLine(
            color = palette.strokeColor,
            start = Offset(ox + s * 0.40f, oy + s * 0.14f),
            end = Offset(ox + s * 0.60f, oy + s * 0.14f),
            strokeWidth = crossStroke.width * 0.5f,
            cap = StrokeCap.Round
        )

        // Viên Hồng Ngọc (Ruby) rực rỡ ở giao điểm Thánh Giá
        val rubyCenter = Offset(ox + s * 0.50f, oy + s * 0.14f)
        drawCircle(color = palette.rubyColor, radius = s * 0.035f, center = rubyCenter)
        drawCircle(color = palette.strokeColor, radius = s * 0.035f, center = rubyCenter, style = strokeStyle)
        drawCircle(color = Color.White, radius = s * 0.012f, center = Offset(rubyCenter.x - s * 0.01f, rubyCenter.y - s * 0.01f))

        // Đai Hoàng Đế to bản đính bộ 3 viên ngọc quý Hoàng Gia
        drawArc(
            color = palette.goldAccent,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(ox + s * 0.26f, oy + s * 0.45f),
            size = Size(s * 0.48f, s * 0.20f),
            style = Stroke(width = strokeStyle.width * 1.3f, cap = StrokeCap.Round)
        )

        // Bộ 3 Viên Ngọc Hoàng Gia: Sapphire (Trái) - Ruby (Giữa) - Emerald (Phải)
        val triGems = listOf(
            Pair(Offset(ox + s * 0.38f, oy + s * 0.51f), palette.sapphireColor),
            Pair(Offset(ox + s * 0.50f, oy + s * 0.47f), palette.rubyColor),
            Pair(Offset(ox + s * 0.62f, oy + s * 0.51f), palette.emeraldColor)
        )
        for ((center, gemColor) in triGems) {
            val radius = if (gemColor == palette.rubyColor) s * 0.038f else s * 0.030f
            drawCircle(color = gemColor, radius = radius, center = center)
            drawCircle(color = palette.strokeColor, radius = radius, center = center, style = strokeStyle)
            drawCircle(color = Color.White, radius = radius * 0.35f, center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f))
        }

        drawBasePedestal3D(ox, oy, s, palette, strokeStyle)
    }
}
