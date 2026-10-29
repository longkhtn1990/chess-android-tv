package com.harrylong.tvchess.ui

import androidx.compose.ui.geometry.Offset
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
import com.harrylong.tvchess.model.ChessMove
import com.harrylong.tvchess.model.Piece
import com.harrylong.tvchess.model.PieceType
import com.harrylong.tvchess.ui.ChessPieceRenderer.drawChessPiece
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hệ thống Animation và Hiệu ứng Đồ Họa 60fps dành cho "Đậu Học Cờ Vua".
 * - Bàn cờ gỗ Hoàng Gia ấm áp với hiệu ứng vát khối Bevel 3D.
 * - Mũi tên dẫn hướng trực quan phát sáng mềm mại cho Bé Đậu.
 * - Quân cờ trượt slide và nhảy Parabol kèm bóng đổ sống động.
 * - Hiệu ứng ăn quân pháo giấy sao băng (Starburst) và Mưa sao ăn mừng chiến thắng (Victory Confetti).
 */
object BoardAnimationEffects {

    // --- 1. VẼ Ô BÀN CỜ GỖ HOÀNG GIA ẤM ÁP (BEVEL & WOOD GRAIN 3D) ---
    fun DrawScope.drawRichWoodSquare(
        topLeft: Offset,
        squareSize: Float,
        isLight: Boolean
    ) {
        val s = squareSize
        val ox = topLeft.x
        val oy = topLeft.y

        if (isLight) {
            // Ô Sáng: Gỗ Thích Hoàng Gia kem mật ong (#F4D5A6 -> #E6BF8A)
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFF4D5A6),
                        Color(0xFFECD0A0),
                        Color(0xFFE6BF8A)
                    ),
                    start = Offset(ox, oy),
                    end = Offset(ox + s, oy + s)
                ),
                topLeft = topLeft,
                size = Size(s, s)
            )

            // Vân gỗ tinh tế chạy chéo nhẹ
            val grainColor = Color(0xFFC99F6C).copy(alpha = 0.22f)
            drawLine(
                color = grainColor,
                start = Offset(ox + s * 0.15f, oy),
                end = Offset(ox + s, oy + s * 0.85f),
                strokeWidth = 1.2f
            )
            drawLine(
                color = grainColor,
                start = Offset(ox, oy + s * 0.35f),
                end = Offset(ox + s * 0.65f, oy + s),
                strokeWidth = 1.0f
            )
        } else {
            // Ô Tối: Gỗ Óc Chó hoàng gia nâu trầm (#6E4123 -> #553018)
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF6E4123),
                        Color(0xFF60381C),
                        Color(0xFF553018)
                    ),
                    start = Offset(ox, oy),
                    end = Offset(ox + s, oy + s)
                ),
                topLeft = topLeft,
                size = Size(s, s)
            )

            // Vân gỗ tối chạy chéo nhẹ
            val grainColor = Color(0xFF3B1E0D).copy(alpha = 0.32f)
            drawLine(
                color = grainColor,
                start = Offset(ox + s * 0.2f, oy),
                end = Offset(ox + s, oy + s * 0.8f),
                strokeWidth = 1.2f
            )
            drawLine(
                color = grainColor,
                start = Offset(ox, oy + s * 0.4f),
                end = Offset(ox + s * 0.6f, oy + s),
                strokeWidth = 1.0f
            )
        }

        // Viền vát nổi 3D (Subtle Inner Bevel):
        val bevelW = (s * 0.022f).coerceAtLeast(1.5f)
        drawLine(
            color = Color.White.copy(alpha = if (isLight) 0.38f else 0.16f),
            start = Offset(ox, oy + bevelW / 2),
            end = Offset(ox + s, oy + bevelW / 2),
            strokeWidth = bevelW
        )
        drawLine(
            color = Color.White.copy(alpha = if (isLight) 0.38f else 0.16f),
            start = Offset(ox + bevelW / 2, oy),
            end = Offset(ox + bevelW / 2, oy + s),
            strokeWidth = bevelW
        )

        // Cạnh Dưới & Phải đổ bóng chìm
        drawLine(
            color = Color.Black.copy(alpha = if (isLight) 0.18f else 0.32f),
            start = Offset(ox, oy + s - bevelW / 2),
            end = Offset(ox + s, oy + s - bevelW / 2),
            strokeWidth = bevelW
        )
        drawLine(
            color = Color.Black.copy(alpha = if (isLight) 0.18f else 0.32f),
            start = Offset(ox + s - bevelW / 2, oy),
            end = Offset(ox + s - bevelW / 2, oy + s),
            strokeWidth = bevelW
        )
    }

    // --- 2. MŨI TÊN DẪN HƯỚNG PHÁT SÁNG TRỰC QUAN CHO BÉ ĐẬU ---
    fun DrawScope.drawMoveGuideArrow(
        fromCenter: Offset,
        toCenter: Offset,
        squareSize: Float,
        color: Color,
        pulseAlpha: Float
    ) {
        val dx = toCenter.x - fromCenter.x
        val dy = toCenter.y - fromCenter.y
        val angle = atan2(dy, dx)

        // Điểm đầu và điểm cuối lùi vào một khoảng để không đè lên giữa quân cờ
        val startOffset = squareSize * 0.22f
        val endOffset = squareSize * 0.28f

        val start = Offset(
            fromCenter.x + cos(angle) * startOffset,
            fromCenter.y + sin(angle) * startOffset
        )
        val end = Offset(
            toCenter.x - cos(angle) * endOffset,
            toCenter.y - sin(angle) * endOffset
        )

        // 1. Quầng sáng lan tỏa ngoài (Outer Glow)
        drawLine(
            color = color.copy(alpha = (pulseAlpha * 0.25f).coerceIn(0f, 1f)),
            start = start,
            end = end,
            strokeWidth = squareSize * 0.16f,
            cap = StrokeCap.Round
        )

        // 2. Thân mũi tên sáng vừa (Mid Glow)
        drawLine(
            color = color.copy(alpha = (pulseAlpha * 0.60f).coerceIn(0f, 1f)),
            start = start,
            end = end,
            strokeWidth = squareSize * 0.09f,
            cap = StrokeCap.Round
        )

        // 3. Lõi sáng rực rỡ (Core Bright Line)
        drawLine(
            color = Color.White.copy(alpha = (pulseAlpha * 0.95f).coerceIn(0f, 1f)),
            start = start,
            end = end,
            strokeWidth = squareSize * 0.040f,
            cap = StrokeCap.Round
        )

        // Đầu mũi tên bo tròn mềm mại hình phi tiêu Hoàng Gia
        val arrowHeadLength = squareSize * 0.28f
        val arrowHeadWidth = squareSize * 0.22f

        withTransform({
            translate(left = end.x, top = end.y)
            rotate(degrees = (angle * 180f / PI).toFloat(), pivot = Offset.Zero)
        }) {
            val arrowPath = Path().apply {
                moveTo(arrowHeadLength * 0.5f, 0f)
                lineTo(-arrowHeadLength * 0.5f, -arrowHeadWidth * 0.5f)
                lineTo(-arrowHeadLength * 0.2f, 0f)
                lineTo(-arrowHeadLength * 0.5f, arrowHeadWidth * 0.5f)
                close()
            }

            // Bóng phát sáng của đầu mũi tên
            drawPath(
                path = arrowPath,
                color = color.copy(alpha = (pulseAlpha * 0.4f).coerceIn(0f, 1f)),
                style = Stroke(width = squareSize * 0.06f, join = StrokeJoin.Round)
            )
            // Thân đầu mũi tên
            drawPath(
                path = arrowPath,
                color = color.copy(alpha = (pulseAlpha * 0.95f).coerceIn(0f, 1f)),
                style = Fill
            )
            // Viền sáng lõi đầu mũi tên
            drawPath(
                path = arrowPath,
                color = Color.White.copy(alpha = (pulseAlpha * 0.95f).coerceIn(0f, 1f)),
                style = Stroke(width = squareSize * 0.025f, join = StrokeJoin.Round)
            )
        }
    }

    // --- 3. QUÂN CỜ ĐANG DI CHUYỂN: TRƯỢT SLIDE, NHẢY PARABOL & NHẬP THÀNH ĐỒNG THỜI ---
    fun DrawScope.drawAnimatedMovingPiece(
        move: ChessMove,
        progress: Float,
        isBoardFlipped: Boolean,
        squareSize: Float
    ) {
        val p = progress.coerceIn(0f, 1f)

        // Tính toạ độ hiển thị ô xuất phát và ô đích
        val fromCol = if (isBoardFlipped) 7 - move.from.col else move.from.col
        val fromRow = if (isBoardFlipped) 7 - move.from.row else move.from.row
        val toCol = if (isBoardFlipped) 7 - move.to.col else move.to.col
        val toRow = if (isBoardFlipped) 7 - move.to.row else move.to.row

        val startX = fromCol * squareSize
        val startY = fromRow * squareSize
        val endX = toCol * squareSize
        val endY = toRow * squareSize

        // Với nước Nhập thành: Vua di chuyển trước (0..70%), Xe di chuyển theo sau (25%..100%)
        // để tạo hiệu ứng nhịp nhàng, loại bỏ hoàn toàn hiện tượng 2 quân đâm chéo cắt nhau
        val isCastle = move.isKingsideCastle || move.isQueensideCastle
        val kingP = if (isCastle) (p / 0.70f).coerceIn(0f, 1f) else p

        val currX = startX + (endX - startX) * kingP
        val currY = startY + (endY - startY) * kingP

        val isKnight = move.piece.type == PieceType.KNIGHT

        // Luật FIDE: Chỉ duy nhất quân Mã nhảy cao Parabol qua các quân cờ khác.
        // Tất cả các quân trượt (Xe, Tượng, Hậu, Tốt, Vua) trượt phẳng sát mặt bàn cờ.
        val hopFactor = sin(p * PI.toFloat()).coerceAtLeast(0f)

        val (arcHeight, scale, elevation) = if (isKnight) {
            val h = squareSize * 0.55f * hopFactor
            val sc = 1f + 0.20f * hopFactor
            val el = hopFactor
            Triple(h, sc, el)
        } else {
            Triple(0f, 1f, 0f)
        }

        // Vẽ quân chính đang di chuyển
        drawChessPiece(
            piece = move.piece,
            topLeft = Offset(currX, currY - arcHeight),
            squareSize = squareSize,
            isSelected = false,
            scale = scale,
            elevationProgress = elevation
        )

        // NẾU LÀ NƯỚC NHẬP THÀNH (CASTLING): VẼ XE PHÁO ĐÀI TRƯỢT ĐỒNG THỜI CÙNG ĐỨC VUA!
        if (move.isKingsideCastle || move.isQueensideCastle) {
            val rookRank = move.from.row
            val rookStartCol = if (move.isKingsideCastle) 7 else 0
            val rookEndCol = if (move.isKingsideCastle) 5 else 3

            val rookScreenStartCol = if (isBoardFlipped) 7 - rookStartCol else rookStartCol
            val rookScreenStartRow = if (isBoardFlipped) 7 - rookRank else rookRank
            val rookScreenEndCol = if (isBoardFlipped) 7 - rookEndCol else rookEndCol
            val rookScreenEndRow = if (isBoardFlipped) 7 - rookRank else rookRank

            val rookStartX = rookScreenStartCol * squareSize
            val rookStartY = rookScreenStartRow * squareSize
            val rookEndX = rookScreenEndCol * squareSize
            val rookEndY = rookScreenEndRow * squareSize

            val rookP = ((p - 0.25f) / 0.75f).coerceIn(0f, 1f)
            val rookCurrentX = rookStartX + (rookEndX - rookStartX) * rookP
            val rookCurrentY = rookStartY + (rookEndY - rookStartY) * rookP

            val rookPiece = Piece(PieceType.ROOK, move.piece.color)

            // Xe nhập thành trượt phẳng sát mặt bàn cờ
            drawChessPiece(
                piece = rookPiece,
                topLeft = Offset(rookCurrentX, rookCurrentY),
                squareSize = squareSize,
                isSelected = false,
                scale = 1f,
                elevationProgress = 0f
            )
        }
    }

    // --- 4. HIỆU ỨNG ĂN QUÂN TUNG SAO & PHÁO GIẤY (STARBURST PARTICLES) ---
    fun DrawScope.drawCaptureStarburst(
        center: Offset,
        squareSize: Float,
        progress: Float
    ) {
        if (progress !in 0.01f..0.99f) return
        val p = progress.coerceIn(0f, 1f)

        // 1. Sóng ánh sáng xung kích tỏa tròn nở dần
        val shockwaveRadius = squareSize * (0.20f + 0.85f * p)
        val shockwaveAlpha = ((1f - p) * 0.75f).coerceIn(0f, 1f)
        drawCircle(
            color = Color(0xFFFDE047).copy(alpha = shockwaveAlpha),
            radius = shockwaveRadius,
            center = center,
            style = Stroke(width = squareSize * 0.045f * (1f - p * 0.5f))
        )
        drawCircle(
            color = Color.White.copy(alpha = shockwaveAlpha * 0.6f),
            radius = shockwaveRadius * 0.85f,
            center = center,
            style = Stroke(width = squareSize * 0.02f)
        )

        // 2. Chùm 8 Ngôi Sao Vàng Hoàng Gia 5 cánh bung tỏa theo hình tròn
        val starCount = 8
        val starColors = listOf(
            Color(0xFFFDE047), // Vàng kim sáng
            Color(0xFFF59E0B), // Vàng cam ấm
            Color(0xFFFFFFFF), // Trắng ngọc lấp lánh
            Color(0xFFEF4444)  // Đỏ ruby vui tươi
        )

        for (i in 0 until starCount) {
            val angle = (i.toFloat() / starCount) * (2f * PI.toFloat()) + p * 1.8f
            val distance = squareSize * (0.15f + 0.70f * p)
            val starX = center.x + cos(angle) * distance
            val starY = center.y + sin(angle) * distance
            val starRadius = squareSize * 0.12f * (1f - p * 0.6f)
            val starAlpha = ((1f - p * 0.9f)).coerceIn(0f, 1f)
            val color = starColors[i % starColors.size].copy(alpha = starAlpha)

            drawStar5Points(
                center = Offset(starX, starY),
                outerRadius = starRadius,
                innerRadius = starRadius * 0.42f,
                color = color,
                rotationAngle = (angle * 180f / PI).toFloat() + p * 180f
            )
        }

        // 3. Các hạt pháo giấy confetti li ti bắn tóe ra
        val sparkleCount = 12
        for (i in 0 until sparkleCount) {
            val angle = (i.toFloat() / sparkleCount) * (2f * PI.toFloat()) + 0.3f
            val dist = squareSize * (0.25f + 0.80f * p * ((i % 3 + 2) / 3f))
            val px = center.x + cos(angle) * dist
            val py = center.y + sin(angle) * dist
            val dotRadius = squareSize * (0.035f * (1f - p * 0.8f))
            val dotAlpha = ((1f - p)).coerceIn(0f, 1f)
            val dotColor = when (i % 3) {
                0 -> Color(0xFF38BDF8) // Ngọc lam
                1 -> Color(0xFF10B981) // Lục bảo
                else -> Color(0xFFF43F5E) // Hồng phấn
            }.copy(alpha = dotAlpha)

            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = Offset(px, py)
            )
        }
    }

    // --- 5. MƯA SAO VÀ PHÁO GIẤY CHÚC MỪNG CHIẾN THẮNG (VICTORY CONFETTI) ---
    fun DrawScope.drawVictoryCelebrationParticles(
        canvasSize: Size,
        phase: Float
    ) {
        val p = phase.coerceIn(0f, 1f)
        val particleCount = 42

        val colors = listOf(
            Color(0xFFFDE047), // Vàng kim
            Color(0xFF38BDF8), // Xanh trời
            Color(0xFFF43F5E), // Đỏ dâu
            Color(0xFF10B981), // Xanh lá
            Color(0xFFA855F7), // Tím mộng mơ
            Color(0xFFFB923C), // Cam mật ong
            Color(0xFFFFFFFF)  // Trắng bạc
        )

        for (i in 0 until particleCount) {
            // Tính vị trí X phân bố đều và có dao động lắc lư
            val normalizedX = (i.toFloat() + 0.5f) / particleCount
            val sway = sin((p * 2f * PI.toFloat()) + i * 1.3f) * (canvasSize.width * 0.035f)
            val x = (normalizedX * canvasSize.width + sway) % canvasSize.width

            // Vị trí Y rơi mượt từ trên xuống dưới
            val speed = 0.8f + (i % 5) * 0.25f
            val startY = -canvasSize.height * 0.1f
            val totalDistance = canvasSize.height * 1.2f
            val y = startY + ((p * speed + (i * 0.07f)) % 1f) * totalDistance

            val color = colors[i % colors.size]
            val rotation = (p * 360f * (if (i % 2 == 0) 1 else -1) + i * 45f)

            if (i % 3 == 0) {
                // Hạt ngôi sao 5 cánh rơi
                val starR = canvasSize.height * 0.022f
                drawStar5Points(
                    center = Offset(x, y),
                    outerRadius = starR,
                    innerRadius = starR * 0.42f,
                    color = color.copy(alpha = 0.90f),
                    rotationAngle = rotation
                )
            } else if (i % 3 == 1) {
                // Mảnh pháo giấy hình chữ nhật xoay lật 3D
                val w = canvasSize.height * 0.024f
                val h = canvasSize.height * 0.012f
                withTransform({
                    translate(left = x, top = y)
                    rotate(degrees = rotation, pivot = Offset.Zero)
                }) {
                    drawRect(
                        color = color.copy(alpha = 0.88f),
                        topLeft = Offset(-w / 2f, -h / 2f),
                        size = Size(w, h)
                    )
                }
            } else {
                // Hạt ngọc tròn lấp lánh
                val dotR = canvasSize.height * 0.010f
                drawCircle(
                    color = color.copy(alpha = 0.85f),
                    radius = dotR,
                    center = Offset(x, y)
                )
            }
        }
    }

    // --- HÀM BỔ TRỢ: VẼ NGÔI SAO 5 CÁNH HOÀN MỸ ---
    private fun DrawScope.drawStar5Points(
        center: Offset,
        outerRadius: Float,
        innerRadius: Float,
        color: Color,
        rotationAngle: Float
    ) {
        val path = Path()
        val angleStep = (PI / 5.0).toFloat()
        val startAngle = (rotationAngle * PI / 180f).toFloat() - (PI / 2.0).toFloat()

        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outerRadius else innerRadius
            val a = startAngle + i * angleStep
            val px = center.x + cos(a) * r
            val py = center.y + sin(a) * r
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()

        drawPath(path = path, color = color, style = Fill)
    }

    // --- 6. MŨI TÊN DẪN HƯỚNG TRONG KHÔNG GIAN 3D TABLETOP ISOMETRIC ---
    fun DrawScope.drawMoveGuideArrow3D(
        fromCenter: Offset,
        toCenter: Offset,
        depthScale: Float,
        color: Color,
        pulseAlpha: Float
    ) {
        val dx = toCenter.x - fromCenter.x
        val dy = toCenter.y - fromCenter.y
        val angle = atan2(dy, dx)

        val arrowScale = (depthScale * 26f).coerceIn(16f, 36f)
        val startOffset = arrowScale * 0.85f
        val endOffset = arrowScale * 1.05f

        val start = Offset(
            fromCenter.x + cos(angle) * startOffset,
            fromCenter.y + sin(angle) * startOffset
        )
        val end = Offset(
            toCenter.x - cos(angle) * endOffset,
            toCenter.y - sin(angle) * endOffset
        )

        // Đường cong Bezier nhẹ trên mặt phẳng 3D
        val midX = (start.x + end.x) * 0.5f
        val midY = (start.y + end.y) * 0.5f - 14f * depthScale
        val arrowCurve = Path().apply {
            moveTo(start.x, start.y)
            quadraticBezierTo(midX, midY, end.x, end.y)
        }

        // Quầng sáng lan toả 3D
        drawPath(
            path = arrowCurve,
            color = color.copy(alpha = (pulseAlpha * 0.28f).coerceIn(0f, 1f)),
            style = Stroke(width = arrowScale * 0.42f, cap = StrokeCap.Round)
        )
        // Thân mũi tên chính
        drawPath(
            path = arrowCurve,
            color = color.copy(alpha = (pulseAlpha * 0.70f).coerceIn(0f, 1f)),
            style = Stroke(width = arrowScale * 0.22f, cap = StrokeCap.Round)
        )
        // Lõi sáng rực rỡ
        drawPath(
            path = arrowCurve,
            color = Color.White.copy(alpha = (pulseAlpha * 0.95f).coerceIn(0f, 1f)),
            style = Stroke(width = arrowScale * 0.08f, cap = StrokeCap.Round)
        )

        // Đầu mũi tên 3D hướng chuẩn về đích
        withTransform({
            translate(left = end.x, top = end.y)
            rotate(degrees = (angle * 180f / PI).toFloat(), pivot = Offset.Zero)
        }) {
            val arrowHead = Path().apply {
                moveTo(arrowScale * 0.65f, 0f)
                lineTo(-arrowScale * 0.50f, -arrowScale * 0.42f)
                lineTo(-arrowScale * 0.22f, 0f)
                lineTo(-arrowScale * 0.50f, arrowScale * 0.42f)
                close()
            }
            drawPath(arrowHead, color = color.copy(alpha = (pulseAlpha * 0.95f).coerceIn(0f, 1f)), style = Fill)
            drawPath(arrowHead, color = Color.White.copy(alpha = (pulseAlpha * 0.95f).coerceIn(0f, 1f)), style = Stroke(width = 2f, join = StrokeJoin.Round))
        }
    }

    // --- 7. HIỆU ỨNG TUNG SAO ĂN QUÂN CO PHỐI CẢNH MẶT BÀN 3D ---
    fun DrawScope.drawCaptureStarburst3D(
        center: Offset,
        depthScale: Float,
        progress: Float
    ) {
        if (progress !in 0.01f..0.99f) return
        val p = progress.coerceIn(0f, 1f)

        // Co trục Y xuống 0.62f để hình elip và chùm sao bung tỏa phẳng theo mặt bàn cờ 3D nghiêng
        withTransform({
            scale(scaleX = 1f, scaleY = 0.62f, pivot = center)
        }) {
            val baseR = 56f * depthScale

            // 1. Sóng ánh sáng xung kích tỏa tròn nở dần
            val shockwaveRadius = baseR * (0.25f + 1.1f * p)
            val shockwaveAlpha = ((1f - p) * 0.75f).coerceIn(0f, 1f)
            drawCircle(
                color = Color(0xFFFDE047).copy(alpha = shockwaveAlpha),
                radius = shockwaveRadius,
                center = center,
                style = Stroke(width = baseR * 0.08f * (1f - p * 0.5f))
            )

            // 2. Chùm 8 Ngôi sao vàng bung tỏa
            val starCount = 8
            val starColors = listOf(
                Color(0xFFFDE047),
                Color(0xFFF59E0B),
                Color(0xFFFFFFFF),
                Color(0xFFEF4444)
            )

            for (i in 0 until starCount) {
                val angle = (i.toFloat() / starCount) * (2f * PI.toFloat()) + p * 2.2f
                val distance = baseR * (0.2f + 0.9f * p)
                val starX = center.x + cos(angle) * distance
                val starY = center.y + sin(angle) * distance
                val starRadius = baseR * 0.16f * (1f - p * 0.5f)
                val starAlpha = ((1f - p * 0.9f)).coerceIn(0f, 1f)
                val color = starColors[i % starColors.size].copy(alpha = starAlpha)

                drawStar5Points(
                    center = Offset(starX, starY),
                    outerRadius = starRadius,
                    innerRadius = starRadius * 0.42f,
                    color = color,
                    rotationAngle = (angle * 180f / PI).toFloat() + p * 240f
                )
            }
        }
    }
}
