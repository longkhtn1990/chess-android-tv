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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.harrylong.tvchess.model.ChessMove
import com.harrylong.tvchess.model.Piece
import com.harrylong.tvchess.model.PieceColor
import com.harrylong.tvchess.model.PieceType
import com.harrylong.tvchess.model.Position
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Bộ dựng hình Bàn Cờ 3D Gỗ Cổ Điển Tabletop & Quân Cờ Gỗ Tiện Tròn 3D Staunton.
 *
 * Tái hiện chính xác phong cách đồ họa nghệ thuật từ ảnh mẫu của người dùng:
 * 1. Phối cảnh Tabletop ~25°: Mở rộng 64 ô cờ to, rõ ràng, cân đối hoàn hảo trên màn hình TV.
 * 2. Mặt bàn gỗ ghép thanh cổ điển (Vertical Wood Planks) màu nâu thông/óc chó ấm áp kèm quầng sáng tâm.
 * 3. Khung bàn cờ Gỗ Sồi/Thích Sáng Màu 3D (#E6C496 -> #D4AC78) với đường chỉ viền đôi nâu sẫm (#3E220E),
 *    in tọa độ A..H / 1..8 sắc nét và Gờ Cạnh Đứng Phía Trước (3D Front Wooden Lip) khắc chữ BEGINNER LEVEL & Move Status.
 * 4. 64 ô cờ Gỗ Thích Kem (#F4D5A6 -> #E6BF8A) & Gỗ Óc Chó Nâu Đậm (#6E4123 -> #553018).
 * 5. Bộ quân cờ Staunton 3D tiện tròn (Lathe-Turned Cylindrical):
 *    - Đế tròn elip 3D đa tầng (tiered elliptical pedestal base), bóng đổ elip mềm dưới chân đế.
 *    - Thân trụ tiện côn với ánh sáng cylinder (specular highlight lệch trái, chuyển mượt sang bóng tối bên phải).
 *    - Quân Trắng: Gỗ ngà ấm (Warm Ivory / Pearl White Wood).
 *    - Quân Đen: Gỗ mun đen bóng (Glossy Ebony Wood) VỚI CÁC VÒNG CHỈ TRẮNG NGÀ/BẠC ĐẶC TRƯNG ở cổ và đế.
 *    - Tạo hình đỉnh 6 loại quân chuẩn Staunton: Tốt cầu tròn, Xe tháp lõm 3D, Mã điêu khắc tinh anh,
 *      Tượng mũ giáo mục núm cầu, Hậu vương miện xoè ngọc tâm, Vua thánh giá 3D kiêu hãnh.
 * 6. Z-Sorting chuẩn xác chống xuyên hình: Quân trượt (Xe, Tượng, Hậu, Tốt, Vua) trượt phẳng sát mặt bàn (arcJump = 0f)
 *    và được vẽ chuẩn theo từng hàng screenRow, các quân hàng trước che phủ tự nhiên ở tiền cảnh!
 * 7. Chỉ duy nhất quân Mã nhảy Parabol trong không gian 3D.
 */
object IsometricBoard3DRenderer {

    /**
     * Dữ liệu hình học tứ giác của 1 ô cờ trong phối cảnh 3D.
     */
    data class QuadSquare3D(
        val p0: Offset, // Top-Left
        val p1: Offset, // Top-Right
        val p2: Offset, // Bottom-Right
        val p3: Offset, // Bottom-Left
        val center: Offset,
        val anchor: Offset, // Điểm đặt chân đế quân cờ
        val depthScale: Float
    ) {
        fun toPath(): Path = Path().apply {
            moveTo(p0.x, p0.y)
            lineTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(p3.x, p3.y)
            close()
        }

        /**
         * Kiểm tra điểm chạm (x, y) có nằm trong tứ giác lồi 3D hay không (Cross-product test).
         */
        fun contains(point: Offset): Boolean {
            val pts = listOf(p0, p1, p2, p3)
            var positive = false
            var negative = false
            for (i in pts.indices) {
                val a = pts[i]
                val b = pts[(i + 1) % 4]
                val cross = (point.x - a.x) * (b.y - a.y) - (point.y - a.y) * (b.x - a.x)
                if (cross > 0f) positive = true
                if (cross < 0f) negative = true
                if (positive && negative) return false
            }
            return true
        }
    }

    /**
     * Tham số phối cảnh 3D của toàn bộ bàn cờ.
     */
    data class PerspectiveLayout(
        val canvasWidth: Float,
        val canvasHeight: Float,
        val centerX: Float,
        // Khung viền ngoài
        val outerTopY: Float,
        val outerBottomY: Float,
        val outerTopWidth: Float,
        val outerBottomWidth: Float,
        // Lòng ô cờ trong
        val innerTopY: Float,
        val innerBottomY: Float,
        val innerTopWidth: Float,
        val innerBottomWidth: Float,
        // Độ sâu chìm của lòng bàn cờ
        val recessedDepth: Float
    ) {
        /**
         * Tính toạ độ 1 điểm trong lưới ô cờ theo tỉ lệ (u, v) với u: 0..1 (col), v: 0..1 (row).
         */
        fun getGridPoint(u: Float, v: Float): Offset {
            val vCurve = (v * (1f + 0.12f * v)) / 1.12f
            val y = innerTopY + (innerBottomY - innerTopY) * vCurve
            val widthAtV = innerTopWidth + (innerBottomWidth - innerTopWidth) * vCurve
            val x = centerX - widthAtV * 0.5f + widthAtV * u
            return Offset(x, y)
        }

        /**
         * Lấy dữ liệu hình học Quad của ô cờ tại hàng screenRow, cột screenCol (0..7).
         */
        fun getQuad(screenRow: Int, screenCol: Int): QuadSquare3D {
            val u0 = screenCol / 8f
            val u1 = (screenCol + 1) / 8f
            val v0 = screenRow / 8f
            val v1 = (screenRow + 1) / 8f

            val p0 = getGridPoint(u0, v0)
            val p1 = getGridPoint(u1, v0)
            val p2 = getGridPoint(u1, v1)
            val p3 = getGridPoint(u0, v1)

            val center = Offset(
                (p0.x + p1.x + p2.x + p3.x) * 0.25f,
                (p0.y + p1.y + p2.y + p3.y) * 0.25f
            )
            // Điểm đặt chân đế quân cờ hơi lùi về phía đáy ô cờ
            val anchor = Offset(
                (p2.x + p3.x) * 0.5f,
                (p2.y + p3.y) * 0.5f - (innerBottomY - innerTopY) * 0.010f
            )
            // Hệ số co giãn theo chiều sâu (0.82f ở hàng xa nhất -> 1.08f ở hàng gần nhất)
            val vMid = (v0 + v1) * 0.5f
            val depthScale = 0.82f + 0.26f * vMid

            return QuadSquare3D(p0, p1, p2, p3, center, anchor, depthScale)
        }
    }

    private data class MovingPieceInfo(
        val move: ChessMove,
        val fromSR: Int,
        val toSR: Int,
        val screenRowFloat: Float,
        val targetScreenRow: Int,
        val anchor: Offset,
        val depthScale: Float,
        val isKnight: Boolean,
        val elevationProgress: Float
    )

    /**
     * Tạo bố cục phối cảnh 3D tối ưu cho kích thước Canvas.
     */
    fun computePerspectiveLayout(intSize: androidx.compose.ui.unit.IntSize): PerspectiveLayout =
        computePerspectiveLayout(Size(intSize.width.toFloat(), intSize.height.toFloat()))

    fun computePerspectiveLayout(size: Size): PerspectiveLayout {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f

        // Bàn cờ nghiêng Tabletop ~25° mở rộng, 64 ô cờ to rõ ràng, cân đối như ảnh mẫu
        val outerBottomWidth = (w * 0.95f).coerceAtMost(h * 1.55f)
        val outerTopWidth = outerBottomWidth * 0.78f

        val outerTopY = h * 0.06f
        val outerDepthY = h * 0.84f
        val outerBottomY = outerTopY + outerDepthY

        // Viền gỗ xung quanh vừa vặn
        val marbleBorderV = outerDepthY * 0.055f
        val marbleBorderHBottom = outerBottomWidth * 0.048f
        val marbleBorderHTop = outerTopWidth * 0.048f

        val innerTopY = outerTopY + marbleBorderV
        val innerBottomY = outerBottomY - marbleBorderV
        val innerTopWidth = outerTopWidth - marbleBorderHTop * 2f
        val innerBottomWidth = outerBottomWidth - marbleBorderHBottom * 2f

        val recessedDepth = (h * 0.015f).coerceIn(6f, 14f)

        return PerspectiveLayout(
            canvasWidth = w,
            canvasHeight = h,
            centerX = cx,
            outerTopY = outerTopY,
            outerBottomY = outerBottomY,
            outerTopWidth = outerTopWidth,
            outerBottomWidth = outerBottomWidth,
            innerTopY = innerTopY,
            innerBottomY = innerBottomY,
            innerTopWidth = innerTopWidth,
            innerBottomWidth = innerBottomWidth,
            recessedDepth = recessedDepth
        )
    }

    /**
     * Xác định ô cờ tương ứng khi người dùng chạm vào toạ độ (x, y) trên màn hình 2D.
     */
    fun findSquareAtPoint(
        point: Offset,
        layout: PerspectiveLayout,
        isBoardFlipped: Boolean
    ): Position? {
        for (screenRow in 0..7) {
            for (screenCol in 0..7) {
                val quad = layout.getQuad(screenRow, screenCol)
                if (quad.contains(point)) {
                    val boardRow = if (isBoardFlipped) 7 - screenRow else screenRow
                    val boardCol = if (isBoardFlipped) 7 - screenCol else screenCol
                    return Position(boardRow, boardCol)
                }
            }
        }
        return null
    }

    // =========================================================================
    // HÀM VẼ CHÍNH: BÀN CỜ 3D TABLETOP ISOMETRIC HOÀN CHỈNH
    // =========================================================================

    fun DrawScope.drawIsometricTabletopBoard(
        layout: PerspectiveLayout,
        board: List<List<Piece?>>,
        isBoardFlipped: Boolean,
        selectedPos: Position?,
        validTargetMoves: List<ChessMove>,
        lastMove: ChessMove?,
        hintMove: ChessMove?,
        kingInCheckPos: Position?,
        pulseAlpha: Float = 1f,
        animatingMove: ChessMove? = null,
        animProgress: Float = 1f,
        levelLabel: String = "BEGINNER LEVEL",
        moveStatusLabel: String = "",
        textMeasurer: androidx.compose.ui.text.TextMeasurer? = null
    ) {
        // 1. MẶT BÀN GỖ GHÉP THANH CỔ ĐIỂN (VERTICAL WOOD PLANKS BACKGROUND)
        drawTabletopWoodBackground(layout)

        // 2. BÓNG ĐỔ KHỐI 3D CỦA BÀN CỜ LÊN MẶT BÀN
        drawBoardOuterDropShadow(layout)

        // 3. KHUNG BÀN CỜ GỖ SỒI/THÍCH SÁNG MÀU, ĐƯỜNG CHỈ VIỀN ĐÔI, TỌA ĐỘ & GỜ CẠNH ĐỨNG KHẮC CHỮ
        drawWoodenBoardFrameAndFrontLip(
            layout = layout,
            isBoardFlipped = isBoardFlipped,
            levelLabel = levelLabel,
            moveStatusLabel = moveStatusLabel,
            textMeasurer = textMeasurer,
            lastMove = lastMove
        )

        // 4. GỜ RÃNH CHÌM ÂM 3D (RECESSED INNER BEVEL)
        drawRecessedBoardWell(layout)

        // 5. 64 Ô CỜ GỖ THÍCH KEM & GỖ ÓC CHÓ NÂU ĐẬM
        drawChessboardSquares(layout, isBoardFlipped, lastMove, hintMove, kingInCheckPos, pulseAlpha)

        // 6. KHUNG VIỀN CHỌN QUÂN ĐỎ 3D (RED SELECTION BOX)
        if (selectedPos != null) {
            val screenRow = if (isBoardFlipped) 7 - selectedPos.row else selectedPos.row
            val screenCol = if (isBoardFlipped) 7 - selectedPos.col else selectedPos.col
            val quad = layout.getQuad(screenRow, screenCol)
            drawRedSelectionBox3D(quad, pulseAlpha)
        }

        // 7. KHUNG VIỀN NƯỚC ĐI XANH LÁ 3D (GREEN TARGET BOXES)
        for (targetMove in validTargetMoves) {
            val targetPos = targetMove.to
            val screenRow = if (isBoardFlipped) 7 - targetPos.row else targetPos.row
            val screenCol = if (isBoardFlipped) 7 - targetPos.col else targetPos.col
            val quad = layout.getQuad(screenRow, screenCol)
            val isCapture = targetMove.capturedPiece != null || targetMove.isEnPassant
            drawGreenTargetBox3D(quad, isCapture, pulseAlpha)
        }

        // 8. BỘ QUÂN CỜ GỖ TIỆN TRÒN 3D VỚI Z-SORTING CHUẨN XÁC THEO TỪNG HÀNG
        drawStauntonPiecesWithZSorting(
            layout = layout,
            board = board,
            isBoardFlipped = isBoardFlipped,
            selectedPos = selectedPos,
            animatingMove = animatingMove,
            animProgress = animProgress
        )
    }

    // =========================================================================
    // CHI TIẾT CÁC THÀNH PHẦN ĐỒ HỌA 3D
    // =========================================================================

    /**
     * Mặt bàn gỗ ghép thanh cổ điển (Vertical Wood Planks) màu nâu thông / óc chó ấm áp.
     */
    private fun DrawScope.drawTabletopWoodBackground(layout: PerspectiveLayout) {
        val w = layout.canvasWidth
        val h = layout.canvasHeight

        // Ghép 10 tấm ván dọc (Vertical wood planks)
        val numPlanks = 10
        val plankW = w / numPlanks

        // Màu sắc ván gỗ tự nhiên: #7C4927, #5E3419, #43230F
        val plankColors = listOf(
            Color(0xFF5E3419),
            Color(0xFF6E3E20),
            Color(0xFF754524),
            Color(0xFF552E15),
            Color(0xFF7C4927),
            Color(0xFF65391D),
            Color(0xFF714122),
            Color(0xFF583017),
            Color(0xFF774625),
            Color(0xFF5F351A)
        )

        for (i in 0 until numPlanks) {
            val px = i * plankW
            val baseColor = plankColors[i % plankColors.size]

            val plankBrush = Brush.verticalGradient(
                colors = listOf(
                    baseColor.copy(red = baseColor.red * 0.72f, green = baseColor.green * 0.72f, blue = baseColor.blue * 0.72f),
                    baseColor,
                    baseColor.copy(red = (baseColor.red * 1.10f).coerceAtMost(1f), green = (baseColor.green * 1.08f).coerceAtMost(1f))
                ),
                startY = 0f,
                endY = h
            )
            drawRect(brush = plankBrush, topLeft = Offset(px, 0f), size = Size(plankW + 1f, h))

            // Các đường vân gỗ dọc uốn lượn nhẹ nhàng
            val grainColor = Color(0xFF351A0A).copy(alpha = 0.20f)
            for (g in 0..1) {
                val gx = px + plankW * (0.32f + g * 0.36f)
                val grainPath = Path().apply {
                    val dir = if (g % 2 == 0) 1 else -1
                    moveTo(gx, 0f)
                    cubicTo(
                        gx + 12f * dir, h * 0.35f,
                        gx - 15f * dir, h * 0.70f,
                        gx + 10f * dir, h
                    )
                }
                drawPath(grainPath, color = grainColor, style = Stroke(width = 1.6f, cap = StrokeCap.Round))
            }

            // Khe tối mảnh giữa các tấm ván và cạnh sáng nhẹ tạo gờ vát
            if (i > 0) {
                drawLine(
                    color = Color(0xFF231006).copy(alpha = 0.85f),
                    start = Offset(px - 1f, 0f),
                    end = Offset(px - 1f, h),
                    strokeWidth = 2f
                )
                drawLine(
                    color = Color(0xFFA56F40).copy(alpha = 0.32f),
                    start = Offset(px + 1f, 0f),
                    end = Offset(px + 1f, h),
                    strokeWidth = 1f
                )
            }
        }

        // Quầng sáng ấm radial vignette ở trung tâm bàn cờ
        val radialVignette = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFAE66).copy(alpha = 0.18f),
                Color(0xFF000000).copy(alpha = 0.0f),
                Color(0xFF180A03).copy(alpha = 0.55f)
            ),
            center = Offset(w * 0.5f, h * 0.45f),
            radius = maxOf(w, h) * 0.75f
        )
        drawRect(brush = radialVignette, size = Size(w, h))
    }

    /**
     * Bóng đổ khối dày 3D của cả bàn cờ lên mặt bàn gỗ.
     */
    private fun DrawScope.drawBoardOuterDropShadow(layout: PerspectiveLayout) {
        val cx = layout.centerX
        val lipThickness = layout.canvasHeight * 0.055f
        val shadowDrop = 14f

        val shadowPath = Path().apply {
            moveTo(cx - layout.outerTopWidth * 0.5f, layout.outerTopY + 8f)
            lineTo(cx + layout.outerTopWidth * 0.5f, layout.outerTopY + 8f)
            lineTo(cx + layout.outerBottomWidth * 0.5f + 16f, layout.outerBottomY + lipThickness + shadowDrop)
            lineTo(cx - layout.outerBottomWidth * 0.5f - 16f, layout.outerBottomY + lipThickness + shadowDrop)
            close()
        }
        drawPath(shadowPath, color = Color.Black.copy(alpha = 0.45f), style = Fill)
    }

    /**
     * Khung bàn cờ Gỗ Sồi / Thích Sáng Màu (#E6C496 -> #D4AC78) với đường chỉ viền đôi nâu sẫm (#3E220E),
     * Gờ cạnh đứng phía trước (3D Front Wooden Lip) khắc chữ BEGINNER LEVEL & Move Status,
     * và in rõ tọa độ A..H / 1..8 sắc nét.
     */
    private fun DrawScope.drawWoodenBoardFrameAndFrontLip(
        layout: PerspectiveLayout,
        isBoardFlipped: Boolean,
        levelLabel: String,
        moveStatusLabel: String,
        textMeasurer: androidx.compose.ui.text.TextMeasurer?,
        lastMove: ChessMove?
    ) {
        val cx = layout.centerX
        val h = layout.canvasHeight

        val outTL = Offset(cx - layout.outerTopWidth * 0.5f, layout.outerTopY)
        val outTR = Offset(cx + layout.outerTopWidth * 0.5f, layout.outerTopY)
        val outBR = Offset(cx + layout.outerBottomWidth * 0.5f, layout.outerBottomY)
        val outBL = Offset(cx - layout.outerBottomWidth * 0.5f, layout.outerBottomY)

        val inTL = Offset(cx - layout.innerTopWidth * 0.5f, layout.innerTopY)
        val inTR = Offset(cx + layout.innerTopWidth * 0.5f, layout.innerTopY)
        val inBR = Offset(cx + layout.innerBottomWidth * 0.5f, layout.innerBottomY)
        val inBL = Offset(cx - layout.innerBottomWidth * 0.5f, layout.innerBottomY)

        val lipThickness = h * 0.068f

        // 1. GỜ CẠNH ĐỨNG PHÍA TRƯỚC (3D Front Wooden Lip)
        val lipPath = Path().apply {
            moveTo(outBL.x, outBL.y)
            lineTo(outBR.x, outBR.y)
            lineTo(outBR.x + 3f, outBR.y + lipThickness)
            lineTo(outBL.x - 3f, outBL.y + lipThickness)
            close()
        }
        val lipBrush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFFCFA570),
                Color(0xFFC1955E),
                Color(0xFFB58952)
            ),
            startY = layout.outerBottomY,
            endY = layout.outerBottomY + lipThickness
        )
        drawPath(lipPath, brush = lipBrush, style = Fill)

        // Đường thớ vân gỗ ngang trên mặt đứng cạnh trước
        val lipGrainColor = Color(0xFF6E431E).copy(alpha = 0.22f)
        for (lg in 1..3) {
            val yGrain = layout.outerBottomY + lipThickness * (lg * 0.25f)
            drawLine(
                color = lipGrainColor,
                start = Offset(outBL.x, yGrain),
                end = Offset(outBR.x, yGrain),
                strokeWidth = 1.2f
            )
        }

        // Cạnh vát sáng ở mép trên của gờ cạnh trước
        drawLine(
            color = Color(0xFFFFF0D4).copy(alpha = 0.85f),
            start = outBL,
            end = outBR,
            strokeWidth = 2.4f
        )
        // Viền bóng dưới cùng của cạnh trước
        drawLine(
            color = Color(0xFF381F0C),
            start = Offset(outBL.x - 3f, outBL.y + lipThickness),
            end = Offset(outBR.x + 3f, outBR.y + lipThickness),
            strokeWidth = 2.0f
        )

        // 2. KHUNG GỖ SỒI / THÍCH SÁNG MÀU TRÊN MẶT BÀN CỜ (#E6C496 -> #D4AC78)
        val framePath = Path().apply {
            moveTo(outTL.x, outTL.y)
            lineTo(outTR.x, outTR.y)
            lineTo(outBR.x, outBR.y)
            lineTo(outBL.x, outBL.y)
            close()
        }
        val woodFrameBrush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFE8C89C),
                Color(0xFFDFC092),
                Color(0xFFD4AC78)
            ),
            start = outTL,
            end = outBR
        )
        drawPath(framePath, brush = woodFrameBrush, style = Fill)

        // Vân gỗ tự nhiên nhẹ trên khung
        val frameGrainCol = Color(0xFF9E7748).copy(alpha = 0.16f)
        for (fg in 0..5) {
            val fx = outTL.x + (outTR.x - outTL.x) * (0.15f + fg * 0.14f)
            drawLine(
                color = frameGrainCol,
                start = Offset(fx, outTL.y),
                end = Offset(fx + (outBL.x - outTL.x) * 0.8f, outBR.y),
                strokeWidth = 1.0f
            )
        }

        // 3. ĐƯỜNG CHỈ VIỀN ĐÔI MÀU NÂU SẪM (#3E220E)
        // Đường chỉ viền ngoài sát mép khung
        val outerPinstripe = Path().apply {
            val insetT = 3.5f
            val insetB = 4.5f
            moveTo(outTL.x + insetT, outTL.y + insetT)
            lineTo(outTR.x - insetT, outTR.y + insetT)
            lineTo(outBR.x - insetB, outBR.y - insetB)
            lineTo(outBL.x + insetB, outBL.y - insetB)
            close()
        }
        drawPath(outerPinstripe, color = Color(0xFF3E220E), style = Stroke(width = 2.0f, join = StrokeJoin.Round))

        // Đường chỉ viền trong ôm sát 64 ô cờ
        val innerPinstripe = Path().apply {
            moveTo(inTL.x, inTL.y)
            lineTo(inTR.x, inTR.y)
            lineTo(inBR.x, inBR.y)
            lineTo(inBL.x, inBL.y)
            close()
        }
        drawPath(innerPinstripe, color = Color(0xFF3E220E), style = Stroke(width = 2.2f, join = StrokeJoin.Round))

        // Cạnh vát phản quang trắng mờ ở mép trên và mép trái ngoài cùng
        drawLine(
            color = Color.White.copy(alpha = 0.35f),
            start = outTL,
            end = outTR,
            strokeWidth = 1.5f
        )
        drawLine(
            color = Color.White.copy(alpha = 0.25f),
            start = outTL,
            end = outBL,
            strokeWidth = 1.5f
        )

        // 4. IN TỌA ĐỘ VÀ KHẮC CHỮ LÊN GỜ CẠNH ĐỨNG
        drawBoardCoordinatesAndLabels(
            layout = layout,
            isBoardFlipped = isBoardFlipped,
            levelLabel = levelLabel,
            moveStatusLabel = moveStatusLabel,
            textMeasurer = textMeasurer,
            lastMove = lastMove,
            lipThickness = lipThickness
        )
    }

    /**
     * In rõ chữ cái tọa độ A..H, số tọa độ 1..8 và khắc chữ lên gờ cạnh đứng phía trước.
     * Cỡ chữ lớn, nét đậm siêu nét (FontWeight.Black) kèm bóng chạm khắc 3D nổi bật trên viền gỗ.
     */
    private fun DrawScope.drawBoardCoordinatesAndLabels(
        layout: PerspectiveLayout,
        isBoardFlipped: Boolean,
        levelLabel: String,
        moveStatusLabel: String,
        textMeasurer: androidx.compose.ui.text.TextMeasurer?,
        lastMove: ChessMove?,
        lipThickness: Float
    ) {
        val coordColor = Color(0xFF241104)
        val engraveLight = Color(0xFFFFF3D6).copy(alpha = 0.85f)
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(0x24, 0x11, 0x04)
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER
        }

        fun drawCenteredText(
            text: String,
            x: Float,
            y: Float,
            textSize: Float,
            isBold: Boolean = true,
            isItalic: Boolean = false,
            withEngrave: Boolean = true
        ) {
            if (textMeasurer != null) {
                val weight = if (isBold) FontWeight.Black else FontWeight.Normal
                val fontStyle = if (isItalic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal

                if (withEngrave) {
                    // Lớp bóng chạm khắc 3D viền gỗ (Engraved highlight)
                    val highlightStyle = TextStyle(
                        color = engraveLight,
                        fontSize = textSize.sp,
                        fontWeight = weight,
                        fontStyle = fontStyle,
                        fontFamily = FontFamily.SansSerif
                    )
                    val highlightLayout = textMeasurer.measure(text = text, style = highlightStyle)
                    drawText(
                        textLayoutResult = highlightLayout,
                        topLeft = Offset(x - highlightLayout.size.width * 0.5f + 1.2f, y - highlightLayout.size.height * 0.5f + 1.5f)
                    )
                }

                // Lớp chữ chính tương phản cao
                val style = TextStyle(
                    color = coordColor,
                    fontSize = textSize.sp,
                    fontWeight = weight,
                    fontStyle = fontStyle,
                    fontFamily = FontFamily.SansSerif
                )
                val layoutResult = textMeasurer.measure(text = text, style = style)
                drawText(
                    textLayoutResult = layoutResult,
                    topLeft = Offset(x - layoutResult.size.width * 0.5f, y - layoutResult.size.height * 0.5f)
                )
            } else {
                if (withEngrave) {
                    textPaint.color = android.graphics.Color.argb(200, 255, 243, 214)
                    textPaint.textSize = textSize * 1.35f
                    val tf = if (isBold) android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD) else android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
                    textPaint.typeface = tf
                    val metrics = textPaint.fontMetrics
                    val baseline = y - (metrics.ascent + metrics.descent) * 0.5f
                    drawContext.canvas.nativeCanvas.drawText(text, x + 1.2f, baseline + 1.5f, textPaint)
                }
                textPaint.color = android.graphics.Color.rgb(0x24, 0x11, 0x04)
                textPaint.textSize = textSize * 1.35f
                val tf = when {
                    isBold && isItalic -> android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD_ITALIC)
                    isBold -> android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                    isItalic -> android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.ITALIC)
                    else -> android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
                }
                textPaint.typeface = tf
                val metrics = textPaint.fontMetrics
                val baseline = y - (metrics.ascent + metrics.descent) * 0.5f
                drawContext.canvas.nativeCanvas.drawText(text, x, baseline, textPaint)
            }
        }

        // Tăng kích thước ký tự lên to rõ ràng, cân đối tuyệt đẹp
        val botTextSize = (layout.outerBottomWidth * 0.028f).coerceIn(18f, 26f)
        val topTextSize = (layout.outerTopWidth * 0.028f).coerceIn(14f, 22f)

        // CHỮ CÁI TỌA ĐỘ A..H DỌC VIỀN DƯỚI & TRÊN
        val botY = layout.innerBottomY + (layout.outerBottomY - layout.innerBottomY) * 0.44f
        val topY = (layout.outerTopY + layout.innerTopY) * 0.5f
        for (col in 0..7) {
            val fileChar = (if (isBoardFlipped) 'H' - col else 'A' + col).toString()
            val u = (col + 0.5f) / 8f
            val ptBot = layout.getGridPoint(u, 1f)
            drawCenteredText(fileChar, ptBot.x, botY, botTextSize, isBold = true)

            val ptTop = layout.getGridPoint(u, 0f)
            drawCenteredText(fileChar, ptTop.x, topY, topTextSize, isBold = true)
        }

        // SỐ TỌA ĐỘ 1..8 DỌC VIỀN TRÁI & PHẢI
        for (row in 0..7) {
            val rankStr = (if (isBoardFlipped) 1 + row else 8 - row).toString()
            val v = (row + 0.5f) / 8f
            val ptLeft = layout.getGridPoint(0f, v)
            val ptRight = layout.getGridPoint(1f, v)

            val outerLeftX = layout.centerX - (layout.outerTopWidth + (layout.outerBottomWidth - layout.outerTopWidth) * v) * 0.5f
            val innerLeftX = layout.centerX - (layout.innerTopWidth + (layout.innerBottomWidth - layout.innerTopWidth) * v) * 0.5f
            val midLeftX = (outerLeftX + innerLeftX) * 0.5f

            val outerRightX = layout.centerX + (layout.outerTopWidth + (layout.outerBottomWidth - layout.outerTopWidth) * v) * 0.5f
            val innerRightX = layout.centerX + (layout.innerTopWidth + (layout.innerBottomWidth - layout.innerTopWidth) * v) * 0.5f
            val midRightX = (outerRightX + innerRightX) * 0.5f

            val rowTextSize = (botTextSize * (0.85f + 0.15f * (row / 7f))).coerceIn(16f, 24f)
            drawCenteredText(rankStr, midLeftX, ptLeft.y, rowTextSize, isBold = true)
            drawCenteredText(rankStr, midRightX, ptRight.y, rowTextSize, isBold = true)
        }

        // KHẮC CHỮ TRÊN GỜ CẠNH ĐỨNG PHÍA TRƯỚC (Front Wooden Lip)
        val lipY = layout.outerBottomY + lipThickness * 0.52f
        val lipTextSize = (layout.outerBottomWidth * 0.019f).coerceIn(12f, 17f)

        // Bên trái: levelLabel ("BEGINNER LEVEL")
        val leftX = layout.centerX - layout.outerBottomWidth * 0.28f
        drawCenteredText(levelLabel, leftX, lipY, lipTextSize, isBold = true)

        // Bên phải: moveStatusLabel ("3. Black's move")
        val rightLabel = moveStatusLabel.ifEmpty {
            if (lastMove == null) {
                "1. White's move"
            } else {
                val nextColor = if (lastMove.piece.color == PieceColor.WHITE) "Black's move" else "White's move"
                nextColor
            }
        }
        val rightX = layout.centerX + layout.outerBottomWidth * 0.28f
        drawCenteredText(rightLabel, rightX, lipY, lipTextSize, isBold = false, isItalic = true)
    }

    /**
     * Vẽ thành rãnh chìm 3D (Recessed Well) - lòng bàn cờ thụt sâu nhẹ so với khung gỗ.
     */
    private fun DrawScope.drawRecessedBoardWell(layout: PerspectiveLayout) {
        val cx = layout.centerX
        val inTL = Offset(cx - layout.innerTopWidth * 0.5f, layout.innerTopY)
        val inTR = Offset(cx + layout.innerTopWidth * 0.5f, layout.innerTopY)
        val inBR = Offset(cx + layout.innerBottomWidth * 0.5f, layout.innerBottomY)
        val inBL = Offset(cx - layout.innerBottomWidth * 0.5f, layout.innerBottomY)

        val depth = layout.recessedDepth

        // Thành rãnh phía trên đổ bóng sâu
        val topWall = Path().apply {
            moveTo(inTL.x, inTL.y)
            lineTo(inTR.x, inTR.y)
            lineTo(inTR.x, inTR.y + depth)
            lineTo(inTL.x, inTL.y + depth)
            close()
        }
        drawPath(topWall, color = Color(0xFF281406).copy(alpha = 0.55f), style = Fill)

        // Thành rãnh bên trái đổ bóng chìm
        val leftWall = Path().apply {
            moveTo(inTL.x, inTL.y)
            lineTo(inTL.x, inTL.y + depth)
            lineTo(inBL.x, inBL.y)
            close()
        }
        drawPath(leftWall, color = Color(0xFF200F04).copy(alpha = 0.40f), style = Fill)

        // Cạnh dưới phản chiếu ánh sáng nhẹ
        drawLine(
            color = Color.White.copy(alpha = 0.22f),
            start = inBL,
            end = inBR,
            strokeWidth = 1.5f
        )
    }

    /**
     * Vẽ 64 ô cờ gỗ hình tứ giác 3D:
     * - Ô sáng: Gỗ Thích kem ấm (#F4D5A6 -> #E6BF8A) với vân gỗ dọc mịn.
     * - Ô tối: Gỗ Óc Chó nâu trầm (#6E4123 -> #553018) với vân gỗ dọc tự nhiên, độ tương phản cao.
     */
    private fun DrawScope.drawChessboardSquares(
        layout: PerspectiveLayout,
        isBoardFlipped: Boolean,
        lastMove: ChessMove?,
        hintMove: ChessMove?,
        kingInCheckPos: Position?,
        pulseAlpha: Float
    ) {
        for (screenRow in 0..7) {
            for (screenCol in 0..7) {
                val boardRow = if (isBoardFlipped) 7 - screenRow else screenRow
                val boardCol = if (isBoardFlipped) 7 - screenCol else screenCol
                val pos = Position(boardRow, boardCol)

                val quad = layout.getQuad(screenRow, screenCol)
                val quadPath = quad.toPath()

                val isLight = (boardRow + boardCol) % 2 == 0

                if (isLight) {
                    // Ô Sáng: Gỗ Thích kem ấm (#F4D5A6 -> #E6BF8A)
                    val lightBrush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFF4D5A6),
                            Color(0xFFECD0A0),
                            Color(0xFFE6BF8A)
                        ),
                        start = quad.p0,
                        end = quad.p2
                    )
                    drawPath(quadPath, brush = lightBrush, style = Fill)

                    // 2 sọc vân gỗ dọc mịn
                    val grainCol = Color(0xFFC99F6C).copy(alpha = 0.22f)
                    for (s in 1..2) {
                        val f = s / 3f
                        drawLine(
                            color = grainCol,
                            start = Offset(quad.p0.x * (1f - f) + quad.p1.x * f, quad.p0.y * (1f - f) + quad.p1.y * f),
                            end = Offset(quad.p3.x * (1f - f) + quad.p2.x * f, quad.p3.y * (1f - f) + quad.p2.y * f),
                            strokeWidth = 1.0f
                        )
                    }
                } else {
                    // Ô Tối: Gỗ Óc Chó nâu trầm (#6E4123 -> #553018)
                    val darkBrush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6E4123),
                            Color(0xFF60381C),
                            Color(0xFF553018)
                        ),
                        start = quad.p0,
                        end = quad.p2
                    )
                    drawPath(quadPath, brush = darkBrush, style = Fill)

                    // 2 sọc vân gỗ dọc sẫm
                    val grainCol = Color(0xFF3B1E0D).copy(alpha = 0.32f)
                    for (s in 1..2) {
                        val f = s / 3f
                        drawLine(
                            color = grainCol,
                            start = Offset(quad.p0.x * (1f - f) + quad.p1.x * f, quad.p0.y * (1f - f) + quad.p1.y * f),
                            end = Offset(quad.p3.x * (1f - f) + quad.p2.x * f, quad.p3.y * (1f - f) + quad.p2.y * f),
                            strokeWidth = 1.2f
                        )
                    }
                }

                // Viền ngăn cách giữa các ô cờ mờ nhẹ
                drawPath(quadPath, color = Color(0xFF2E170A).copy(alpha = 0.18f), style = Stroke(width = 0.8f))

                // Ô nước đi trước (Last Move) ánh vàng kim ấm
                if (lastMove != null && (pos == lastMove.from || pos == lastMove.to)) {
                    drawPath(quadPath, color = Color(0xFFFACC15).copy(alpha = 0.26f), style = Fill)
                }

                // Ô gợi ý (Hint Move) ánh ngọc lam phát sáng
                if (hintMove != null && (pos == hintMove.from || pos == hintMove.to)) {
                    drawPath(quadPath, color = Color(0xFF38BDF8).copy(alpha = 0.35f * pulseAlpha), style = Fill)
                }

                // Cảnh báo đỏ rực ô Vua bị chiếu
                if (pos == kingInCheckPos) {
                    drawPath(quadPath, color = Color(0xFFEF4444).copy(alpha = 0.55f), style = Fill)
                }
            }
        }
    }

    /**
     * KHUNG VIỀN CHỌN QUÂN ĐỎ 3D (RED SELECTION BOX)
     */
    fun DrawScope.drawRedSelectionBox3D(quad: QuadSquare3D, pulseAlpha: Float = 1f) {
        val redColor = Color(0xFFE11D48)
        val redHighlight = Color(0xFFFDA4AF)

        val inset = 2.5f
        val q0 = Offset(quad.p0.x + inset, quad.p0.y + inset)
        val q1 = Offset(quad.p1.x - inset, quad.p1.y + inset)
        val q2 = Offset(quad.p2.x - inset, quad.p2.y - inset)
        val q3 = Offset(quad.p3.x + inset, quad.p3.y - inset)

        val boxPath = Path().apply {
            moveTo(q0.x, q0.y)
            lineTo(q1.x, q1.y)
            lineTo(q2.x, q2.y)
            lineTo(q3.x, q3.y)
            close()
        }

        // Quầng sáng đỏ mềm
        drawPath(
            path = boxPath,
            color = redColor.copy(alpha = 0.35f * pulseAlpha),
            style = Stroke(width = 10f * quad.depthScale, join = StrokeJoin.Round)
        )

        // Viền đỏ khối đậm nét dày
        val strokeWidth = 5.2f * quad.depthScale
        drawPath(
            path = boxPath,
            color = redColor,
            style = Stroke(width = strokeWidth, join = StrokeJoin.Round)
        )

        // Đường sáng viền trên
        drawLine(
            color = redHighlight.copy(alpha = 0.85f * pulseAlpha),
            start = q0,
            end = q1,
            strokeWidth = strokeWidth * 0.45f,
            cap = StrokeCap.Round
        )
    }

    /**
     * KHUNG VIỀN NƯỚC ĐI XANH LÁ 3D (GREEN TARGET BOX)
     */
    fun DrawScope.drawGreenTargetBox3D(quad: QuadSquare3D, isCapture: Boolean, pulseAlpha: Float = 1f) {
        val greenColor = if (isCapture) Color(0xFFF97316) else Color(0xFF22C55E)
        val greenHighlight = if (isCapture) Color(0xFFFED7AA) else Color(0xFF86EFAC)

        val inset = 2.5f
        val q0 = Offset(quad.p0.x + inset, quad.p0.y + inset)
        val q1 = Offset(quad.p1.x - inset, quad.p1.y + inset)
        val q2 = Offset(quad.p2.x - inset, quad.p2.y - inset)
        val q3 = Offset(quad.p3.x + inset, quad.p3.y - inset)

        val boxPath = Path().apply {
            moveTo(q0.x, q0.y)
            lineTo(q1.x, q1.y)
            lineTo(q2.x, q2.y)
            lineTo(q3.x, q3.y)
            close()
        }

        // Quầng sáng lan tỏa mềm
        drawPath(
            path = boxPath,
            color = greenColor.copy(alpha = 0.30f * pulseAlpha),
            style = Stroke(width = 9f * quad.depthScale, join = StrokeJoin.Round)
        )

        // Viền xanh lá hộp 3D dày
        val strokeWidth = 4.8f * quad.depthScale
        drawPath(
            path = boxPath,
            color = greenColor,
            style = Stroke(width = strokeWidth, join = StrokeJoin.Round)
        )

        // Đường viền phản chiếu ánh sáng
        drawLine(
            color = greenHighlight.copy(alpha = 0.85f * pulseAlpha),
            start = q0,
            end = q1,
            strokeWidth = strokeWidth * 0.45f,
            cap = StrokeCap.Round
        )

        if (isCapture) {
            drawCircle(
                color = greenColor.copy(alpha = 0.75f),
                radius = 7f * quad.depthScale,
                center = quad.center
            )
        }
    }

    // =========================================================================
    // VẼ BỘ QUÂN CỜ 3D STAUNTON CỔ ĐIỂN VỚI Z-SORTING (PAINTER'S ALGORITHM)
    // =========================================================================

    private fun DrawScope.drawStauntonPiecesWithZSorting(
        layout: PerspectiveLayout,
        board: List<List<Piece?>>,
        isBoardFlipped: Boolean,
        selectedPos: Position?,
        animatingMove: ChessMove?,
        animProgress: Float
    ) {
        val p = animProgress.coerceIn(0f, 1f)

        // Dữ liệu quân cờ đang di chuyển trong animation
        val movingInfo = if (animatingMove != null && p < 0.99f) {
            val fromSR = if (isBoardFlipped) 7 - animatingMove.from.row else animatingMove.from.row
            val fromSC = if (isBoardFlipped) 7 - animatingMove.from.col else animatingMove.from.col
            val toSR = if (isBoardFlipped) 7 - animatingMove.to.row else animatingMove.to.row
            val toSC = if (isBoardFlipped) 7 - animatingMove.to.col else animatingMove.to.col

            val qFrom = layout.getQuad(fromSR, fromSC)
            val qTo = layout.getQuad(toSR, toSC)

            val currSRFloat = fromSR + (toSR - fromSR) * p
            val currAnchorX = qFrom.anchor.x + (qTo.anchor.x - qFrom.anchor.x) * p
            val currAnchorY = qFrom.anchor.y + (qTo.anchor.y - qFrom.anchor.y) * p
            val currDepthScale = qFrom.depthScale + (qTo.depthScale - qFrom.depthScale) * p

            val isKnight = animatingMove.piece.type == PieceType.KNIGHT
            val hopFactor = sin(p * PI.toFloat()).coerceAtLeast(0f)
            // Luật FIDE: Chỉ duy nhất Mã nhảy Parabol trong không gian 3D, các quân khác trượt phẳng sát bàn
            val arcJump = if (isKnight) hopFactor else 0f

            MovingPieceInfo(
                move = animatingMove,
                fromSR = fromSR,
                toSR = toSR,
                screenRowFloat = currSRFloat,
                targetScreenRow = currSRFloat.toInt().coerceIn(0, 7),
                anchor = Offset(currAnchorX, currAnchorY),
                depthScale = currDepthScale,
                isKnight = isKnight,
                elevationProgress = arcJump
            )
        } else null

        // DUYỆT TỪ HÀNG XA NHẤT (screenRow = 0) TỚI GẦN NHẤT (screenRow = 7)
        for (screenRow in 0..7) {
            // 1. Vẽ các quân cờ tĩnh ở hàng screenRow
            for (screenCol in 0..7) {
                val boardRow = if (isBoardFlipped) 7 - screenRow else screenRow
                val boardCol = if (isBoardFlipped) 7 - screenCol else screenCol
                val pos = Position(boardRow, boardCol)

                // Ẩn quân tại ô xuất phát của move đang bay
                if (animatingMove != null && p < 0.99f && pos == animatingMove.from) {
                    continue
                }

                // Ô đích của move đang bay:
                if (animatingMove != null && p < 0.99f && pos == animatingMove.to) {
                    val isCapture = (animatingMove.capturedPiece != null && !animatingMove.isEnPassant)
                    // Nếu là nước ăn quân: giữ hiển thị quân bị ăn tại ô đích khi animProgress < 0.80f!
                    if (!isCapture || p >= 0.80f) {
                        continue
                    }
                }

                // Nếu là nước Nhập thành: ẩn Xe tại ô xuất phát và ô đích
                if (animatingMove != null && p < 0.99f && (animatingMove.isKingsideCastle || animatingMove.isQueensideCastle)) {
                    val rookRank = animatingMove.from.row
                    val rookStartCol = if (animatingMove.isKingsideCastle) 7 else 0
                    val rookEndCol = if (animatingMove.isKingsideCastle) 5 else 3
                    if ((pos.row == rookRank && pos.col == rookStartCol) ||
                        (pos.row == rookRank && pos.col == rookEndCol)
                    ) {
                        continue
                    }
                }

                val piece = board[boardRow][boardCol] ?: continue
                val quad = layout.getQuad(screenRow, screenCol)
                val isSelected = (pos == selectedPos)

                drawSingleStauntonPiece3D(
                    piece = piece,
                    anchor = quad.anchor,
                    depthScale = quad.depthScale,
                    isSelected = isSelected
                )
            }

            // 2. VẼ QUÂN TRƯỢT (Xe, Tượng, Hậu, Tốt, Vua) NGAY TRONG HÀNG NÀY (Z-SORTING CHUẨN XÁC)
            // Khi currScreenRowFloat.toInt() == screenRow, vẽ quân trượt sau khi vẽ các quân tĩnh của hàng screenRow,
            // trước khi vẽ hàng screenRow + 1. Nhờ đó quân hàng screenRow + 1..7 sẽ tự nhiên nằm ở tiền cảnh che phủ quân trượt!
            if (movingInfo != null && !movingInfo.isKnight && movingInfo.targetScreenRow == screenRow) {
                drawSingleStauntonPiece3D(
                    piece = movingInfo.move.piece,
                    anchor = movingInfo.anchor,
                    depthScale = movingInfo.depthScale,
                    isSelected = false,
                    elevationProgress = 0f
                )

                // Nếu là nước Nhập thành: vẽ Xe trượt đồng thời sát mặt bàn
                if (movingInfo.move.isKingsideCastle || movingInfo.move.isQueensideCastle) {
                    drawCastlingRookIn3D(layout, movingInfo.move, p, isBoardFlipped)
                }
            }
        }

        // 3. VẼ QUÂN MÃ BAY TRONG KHÔNG GIAN 3D
        // Quân Mã nhảy bổng lên không trung qua các quân cờ khác nên vẽ nổi bật ở trên cùng
        if (movingInfo != null && movingInfo.isKnight) {
            drawSingleStauntonPiece3D(
                piece = movingInfo.move.piece,
                anchor = movingInfo.anchor,
                depthScale = movingInfo.depthScale,
                isSelected = false,
                elevationProgress = movingInfo.elevationProgress
            )
        }
    }

    /**
     * Dựng 1 quân cờ Staunton 3D tiện tròn nhìn từ góc cao chếch xuống giống hệt ảnh mẫu.
     */
    fun DrawScope.drawSingleStauntonPiece3D(
        piece: Piece,
        anchor: Offset,
        depthScale: Float,
        isSelected: Boolean = false,
        elevationProgress: Float = if (isSelected) 1f else 0f
    ) {
        val isWhite = piece.color == PieceColor.WHITE

        // Cao độ nhấc lên khi chọn quân hoặc nhảy trong animation
        val liftY = -48f * depthScale * elevationProgress

        // 1. BÓNG ĐỔ ELIP MỀM DƯỚI ĐẾ (PERSPECTIVE DROP SHADOW)
        val shadowW = 36f * depthScale * (1f + 0.25f * elevationProgress)
        val shadowH = 14f * depthScale * (1f + 0.15f * elevationProgress)
        val shadowAlpha = (if (isSelected) 0.50f else 0.40f) * (1f - 0.40f * elevationProgress)

        drawOval(
            color = Color(0xFF160B04).copy(alpha = shadowAlpha),
            topLeft = Offset(anchor.x - shadowW * 0.46f + 3f, anchor.y - shadowH * 0.5f + 2f),
            size = Size(shadowW, shadowH)
        )

        // 2. TỌA ĐỘ VÀ KÍCH THƯỚC QUÂN CỜ
        val bx = anchor.x
        val by = anchor.y + liftY

        val pieceHeight = when (piece.type) {
            PieceType.PAWN -> 46f * depthScale
            PieceType.KNIGHT -> 58f * depthScale
            PieceType.BISHOP -> 62f * depthScale
            PieceType.ROOK -> 54f * depthScale
            PieceType.QUEEN -> 70f * depthScale
            PieceType.KING -> 78f * depthScale
        }
        val baseWidth = (pieceHeight * 0.54f).coerceAtLeast(24f * depthScale)

        // 3. VẼ TỪNG LOẠI QUÂN CỜ GỖ TIỆN TRÒN 3D
        when (piece.type) {
            PieceType.PAWN -> drawTurnedPawn(bx, by, pieceHeight, baseWidth, depthScale, isWhite)
            PieceType.ROOK -> drawTurnedRook(bx, by, pieceHeight, baseWidth, depthScale, isWhite)
            PieceType.KNIGHT -> drawTurnedKnight(bx, by, pieceHeight, baseWidth, depthScale, isWhite)
            PieceType.BISHOP -> drawTurnedBishop(bx, by, pieceHeight, baseWidth, depthScale, isWhite)
            PieceType.QUEEN -> drawTurnedQueen(bx, by, pieceHeight, baseWidth, depthScale, isWhite)
            PieceType.KING -> drawTurnedKing(bx, by, pieceHeight, baseWidth, depthScale, isWhite)
        }
    }

    // --- BỘ ĐỆM MÀU CYLINDER SHADING (Ánh sáng lệch trái, chuyển mượt sang bóng tối bên phải) ---
    private fun createCylinderBrush(leftX: Float, rightX: Float, isWhite: Boolean): Brush {
        return if (isWhite) {
            // Quân Trắng: Sáng #FCF9F2, thân #DFD7CB, bóng #9B9184, viền #4A3F35
            Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.0f to Color(0xFFD2C9BD),
                    0.25f to Color(0xFFFCF9F2),
                    0.50f to Color(0xFFDFD7CB),
                    0.80f to Color(0xFFB5ABA0),
                    1.0f to Color(0xFF8A7F73)
                ),
                startX = leftX,
                endX = rightX
            )
        } else {
            // Quân Đen: Thân gỗ mun đen bóng (#4E4945 highlight, #211D1B midtone, #0B0908 shadow)
            Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.0f to Color(0xFF2B2624),
                    0.25f to Color(0xFF554E4A),
                    0.50f to Color(0xFF231E1C),
                    0.80f to Color(0xFF100D0C),
                    1.0f to Color(0xFF060505)
                ),
                startX = leftX,
                endX = rightX
            )
        }
    }

    // --- ĐẾ TRÒN ELIP 3D ĐA TẦNG (TIERED ELLIPTICAL PEDESTAL BASE) ---
    private fun DrawScope.drawTurnedBase(
        bx: Float,
        by: Float,
        baseW: Float,
        depthScale: Float,
        isWhite: Boolean
    ) {
        val strokeWidth = (1.8f * depthScale).coerceAtLeast(1.2f)
        val outlineColor = if (isWhite) Color(0xFF4A3F35) else Color(0xFF000000)

        // TẦNG 1: ĐẾ ELIP LỚN DƯỚI CÙNG
        val t1W = baseW
        val t1H = baseW * 0.34f
        val t1Thickness = baseW * 0.10f

        val t1Body = Path().apply {
            moveTo(bx - t1W * 0.5f, by - t1H * 0.5f)
            lineTo(bx - t1W * 0.5f, by - t1H * 0.5f - t1Thickness)
            lineTo(bx + t1W * 0.5f, by - t1H * 0.5f - t1Thickness)
            lineTo(bx + t1W * 0.5f, by - t1H * 0.5f)
            close()
        }
        val t1Brush = createCylinderBrush(bx - t1W * 0.5f, bx + t1W * 0.5f, isWhite)
        drawPath(t1Body, brush = t1Brush, style = Fill)
        drawPath(t1Body, color = outlineColor, style = Stroke(width = strokeWidth))

        drawOval(
            brush = t1Brush,
            topLeft = Offset(bx - t1W * 0.5f, by - t1H),
            size = Size(t1W, t1H)
        )
        drawOval(
            color = outlineColor,
            topLeft = Offset(bx - t1W * 0.5f, by - t1H),
            size = Size(t1W, t1H),
            style = Stroke(width = strokeWidth)
        )

        // TẦNG 2: BẬC GIẬT CẤP Ở GIỮA
        val t2W = baseW * 0.82f
        val t2H = baseW * 0.28f
        val t2Y = by - t1Thickness - t1H * 0.35f
        val t2Thickness = baseW * 0.08f

        val t2Body = Path().apply {
            moveTo(bx - t2W * 0.5f, t2Y)
            lineTo(bx - t2W * 0.5f, t2Y - t2Thickness)
            lineTo(bx + t2W * 0.5f, t2Y - t2Thickness)
            lineTo(bx + t2W * 0.5f, t2Y)
            close()
        }
        val t2Brush = createCylinderBrush(bx - t2W * 0.5f, bx + t2W * 0.5f, isWhite)
        drawPath(t2Body, brush = t2Brush, style = Fill)
        drawPath(t2Body, color = outlineColor, style = Stroke(width = strokeWidth))

        drawOval(
            brush = t2Brush,
            topLeft = Offset(bx - t2W * 0.5f, t2Y - t2Thickness - t2H * 0.5f),
            size = Size(t2W, t2H)
        )
        drawOval(
            color = outlineColor,
            topLeft = Offset(bx - t2W * 0.5f, t2Y - t2Thickness - t2H * 0.5f),
            size = Size(t2W, t2H),
            style = Stroke(width = strokeWidth)
        )

        // VÒNG CHỈ TIỆN TRÒN ĐỒNG TÂM Ở ĐẾ (Concentric Turned Rings)
        // Quân Đen có vòng chỉ trắng ngà/bạc rực sáng giống hệt ảnh mẫu!
        if (!isWhite) {
            drawOval(
                color = Color(0xFFD6CFC7).copy(alpha = 0.80f),
                topLeft = Offset(bx - t2W * 0.44f, t2Y - t2Thickness - t2H * 0.42f),
                size = Size(t2W * 0.88f, t2H * 0.84f),
                style = Stroke(width = 1.6f * depthScale)
            )
        } else {
            drawOval(
                color = Color(0xFF7A6E60).copy(alpha = 0.45f),
                topLeft = Offset(bx - t2W * 0.44f, t2Y - t2Thickness - t2H * 0.42f),
                size = Size(t2W * 0.88f, t2H * 0.84f),
                style = Stroke(width = 1.3f * depthScale)
            )
        }

        // TẦNG 3: CỔ ĐẾ THON LÊN (Scotia collar)
        val t3WBottom = baseW * 0.68f
        val t3WTop = baseW * 0.42f
        val t3BottomY = t2Y - t2Thickness - t2H * 0.2f
        val t3TopY = by - baseW * 0.28f

        val t3Path = Path().apply {
            moveTo(bx - t3WBottom * 0.5f, t3BottomY)
            lineTo(bx - t3WTop * 0.5f, t3TopY)
            lineTo(bx + t3WTop * 0.5f, t3TopY)
            lineTo(bx + t3WBottom * 0.5f, t3BottomY)
            close()
        }
        val t3Brush = createCylinderBrush(bx - t3WBottom * 0.5f, bx + t3WBottom * 0.5f, isWhite)
        drawPath(t3Path, brush = t3Brush, style = Fill)
        drawPath(t3Path, color = outlineColor, style = Stroke(width = strokeWidth))
    }

    // 1. TỐT (PAWN)
    private fun DrawScope.drawTurnedPawn(
        bx: Float,
        by: Float,
        h: Float,
        w: Float,
        depthScale: Float,
        isWhite: Boolean
    ) {
        val strokeWidth = (1.8f * depthScale).coerceAtLeast(1.2f)
        val outlineColor = if (isWhite) Color(0xFF4A3F35) else Color(0xFF000000)

        // Chân đế 3 tầng
        drawTurnedBase(bx, by, w, depthScale, isWhite)

        // Thân côn thon (Tapered stem)
        val stemStartY = by - w * 0.28f
        val neckY = by - h * 0.72f
        val stemWBottom = w * 0.40f
        val stemWWaist = w * 0.26f
        val stemWTop = w * 0.32f

        val stemPath = Path().apply {
            moveTo(bx - stemWBottom * 0.5f, stemStartY)
            cubicTo(
                bx - stemWWaist * 0.5f, stemStartY - (stemStartY - neckY) * 0.45f,
                bx - stemWWaist * 0.5f, stemStartY - (stemStartY - neckY) * 0.75f,
                bx - stemWTop * 0.5f, neckY
            )
            lineTo(bx + stemWTop * 0.5f, neckY)
            cubicTo(
                bx + stemWWaist * 0.5f, stemStartY - (stemStartY - neckY) * 0.75f,
                bx + stemWWaist * 0.5f, stemStartY - (stemStartY - neckY) * 0.45f,
                bx + stemWBottom * 0.5f, stemStartY
            )
            close()
        }
        val stemBrush = createCylinderBrush(bx - stemWBottom * 0.5f, bx + stemWBottom * 0.5f, isWhite)
        drawPath(stemPath, brush = stemBrush, style = Fill)
        drawPath(stemPath, color = outlineColor, style = Stroke(width = strokeWidth))

        // Vòng cổ tiện tròn (Turned collar ring)
        val collarW = w * 0.46f
        val collarH = w * 0.16f
        val collarBrush = createCylinderBrush(bx - collarW * 0.5f, bx + collarW * 0.5f, isWhite)

        drawOval(
            brush = collarBrush,
            topLeft = Offset(bx - collarW * 0.5f, neckY - collarH * 0.5f),
            size = Size(collarW, collarH)
        )
        drawOval(
            color = outlineColor,
            topLeft = Offset(bx - collarW * 0.5f, neckY - collarH * 0.5f),
            size = Size(collarW, collarH),
            style = Stroke(width = strokeWidth)
        )

        // Vòng chỉ sáng ngà ở cổ quân Đen (Đặc trưng ảnh mẫu!)
        if (!isWhite) {
            drawOval(
                color = Color(0xFFD6CFC7).copy(alpha = 0.85f),
                topLeft = Offset(bx - collarW * 0.42f, neckY - collarH * 0.40f),
                size = Size(collarW * 0.84f, collarH * 0.80f),
                style = Stroke(width = 1.4f * depthScale)
            )
        } else {
            drawOval(
                color = Color(0xFF6B5F52).copy(alpha = 0.50f),
                topLeft = Offset(bx - collarW * 0.42f, neckY - collarH * 0.40f),
                size = Size(collarW * 0.84f, collarH * 0.80f),
                style = Stroke(width = 1.1f * depthScale)
            )
        }

        // Đầu quả cầu 3D bóng tròn (Spherical 3D head)
        val headRadius = w * 0.26f
        val headCenter = Offset(bx, neckY - headRadius * 0.95f)

        val sphereBrush = if (isWhite) {
            Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to Color(0xFFFCF9F2),
                    0.45f to Color(0xFFDFD7CB),
                    0.80f to Color(0xFF9B9184),
                    1.0f to Color(0xFF5A4E42)
                ),
                center = Offset(headCenter.x - headRadius * 0.30f, headCenter.y - headRadius * 0.32f),
                radius = headRadius * 1.35f
            )
        } else {
            Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to Color(0xFF6E6762),
                    0.35f to Color(0xFF4E4945),
                    0.65f to Color(0xFF211D1B),
                    0.90f to Color(0xFF0B0908),
                    1.0f to Color(0xFF020202)
                ),
                center = Offset(headCenter.x - headRadius * 0.30f, headCenter.y - headRadius * 0.32f),
                radius = headRadius * 1.35f
            )
        }
        drawCircle(brush = sphereBrush, radius = headRadius, center = headCenter)
        drawCircle(color = outlineColor, radius = headRadius, center = headCenter, style = Stroke(width = strokeWidth))

        // Điểm phản quang trắng sáng bóng bẩy trên đầu cầu
        val highlightColor = if (isWhite) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.55f)
        drawCircle(
            color = highlightColor,
            radius = headRadius * 0.28f,
            center = Offset(headCenter.x - headRadius * 0.28f, headCenter.y - headRadius * 0.30f)
        )
    }

    // 2. XE (ROOK)
    private fun DrawScope.drawTurnedRook(
        bx: Float,
        by: Float,
        h: Float,
        w: Float,
        depthScale: Float,
        isWhite: Boolean
    ) {
        val strokeWidth = (1.8f * depthScale).coerceAtLeast(1.2f)
        val outlineColor = if (isWhite) Color(0xFF4A3F35) else Color(0xFF000000)

        // Chân đế 3 tầng
        drawTurnedBase(bx, by, w, depthScale, isWhite)

        // Thân tháp thành hình trụ tiện tròn
        val bodyStartY = by - w * 0.28f
        val parapetY = by - h * 0.74f
        val bodyWBottom = w * 0.48f
        val bodyWTop = w * 0.42f

        val bodyPath = Path().apply {
            moveTo(bx - bodyWBottom * 0.5f, bodyStartY)
            lineTo(bx - bodyWTop * 0.5f, parapetY)
            lineTo(bx + bodyWTop * 0.5f, parapetY)
            lineTo(bx + bodyWBottom * 0.5f, bodyStartY)
            close()
        }
        val bodyBrush = createCylinderBrush(bx - bodyWBottom * 0.5f, bx + bodyWBottom * 0.5f, isWhite)
        drawPath(bodyPath, brush = bodyBrush, style = Fill)
        drawPath(bodyPath, color = outlineColor, style = Stroke(width = strokeWidth))

        // Vòng chỉ tiện ngay dưới đầu tháp
        if (!isWhite) {
            drawOval(
                color = Color(0xFFD6CFC7).copy(alpha = 0.80f),
                topLeft = Offset(bx - bodyWTop * 0.48f, parapetY - w * 0.04f),
                size = Size(bodyWTop * 0.96f, w * 0.10f),
                style = Stroke(width = 1.4f * depthScale)
            )
        }

        // Đầu tháp tròn loe có lòng trũng elip 3D phía trên & các khía tường thành
        val topRimW = w * 0.60f
        val topRimH = w * 0.22f
        val topApexY = by - h

        val parapetFlare = Path().apply {
            moveTo(bx - bodyWTop * 0.5f, parapetY)
            lineTo(bx - topRimW * 0.5f, topApexY + topRimH * 0.5f)
            lineTo(bx + topRimW * 0.5f, topApexY + topRimH * 0.5f)
            lineTo(bx + bodyWTop * 0.5f, parapetY)
            close()
        }
        val flareBrush = createCylinderBrush(bx - topRimW * 0.5f, bx + topRimW * 0.5f, isWhite)
        drawPath(parapetFlare, brush = flareBrush, style = Fill)
        drawPath(parapetFlare, color = outlineColor, style = Stroke(width = strokeWidth))

        // Vành elip ngoài cùng của tháp
        drawOval(
            brush = flareBrush,
            topLeft = Offset(bx - topRimW * 0.5f, topApexY),
            size = Size(topRimW, topRimH)
        )
        drawOval(
            color = outlineColor,
            topLeft = Offset(bx - topRimW * 0.5f, topApexY),
            size = Size(topRimW, topRimH),
            style = Stroke(width = strokeWidth)
        )

        // Lòng trũng elip 3D bên trong tháp (Recessed Hollow Interior nhìn từ góc cao)
        val hollowW = topRimW * 0.68f
        val hollowH = topRimH * 0.68f
        val hollowBrush = if (isWhite) {
            Brush.verticalGradient(
                colors = listOf(Color(0xFF6B5F52), Color(0xFF9B9184), Color(0xFFDFD7CB)),
                startY = topApexY,
                endY = topApexY + hollowH
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(Color(0xFF070606), Color(0xFF141110), Color(0xFF26211F)),
                startY = topApexY,
                endY = topApexY + hollowH
            )
        }
        drawOval(
            brush = hollowBrush,
            topLeft = Offset(bx - hollowW * 0.5f, topApexY + (topRimH - hollowH) * 0.5f),
            size = Size(hollowW, hollowH)
        )

        // 4 Khía lỗ châu mai tường thành (Crenellations)
        val crenelH = topRimH * 0.65f
        val crenelOffsets = listOf(-topRimW * 0.32f, -topRimW * 0.10f, topRimW * 0.10f, topRimW * 0.32f)
        for (cxOffset in crenelOffsets) {
            drawLine(
                color = outlineColor,
                start = Offset(bx + cxOffset, topApexY + 2f),
                end = Offset(bx + cxOffset, topApexY + crenelH + 4f),
                strokeWidth = strokeWidth * 0.9f
            )
        }
    }

    // 3. MÃ (KNIGHT)
    private fun DrawScope.drawTurnedKnight(
        bx: Float,
        by: Float,
        h: Float,
        w: Float,
        depthScale: Float,
        isWhite: Boolean
    ) {
        val strokeWidth = (1.8f * depthScale).coerceAtLeast(1.2f)
        val outlineColor = if (isWhite) Color(0xFF4A3F35) else Color(0xFF000000)

        // Chân đế 3 tầng
        drawTurnedBase(bx, by, w, depthScale, isWhite)

        // Vòng cổ bệ đỡ đầu ngựa
        val collarY = by - w * 0.28f
        val collarW = w * 0.48f
        val collarH = w * 0.16f
        val collarBrush = createCylinderBrush(bx - collarW * 0.5f, bx + collarW * 0.5f, isWhite)
        drawOval(
            brush = collarBrush,
            topLeft = Offset(bx - collarW * 0.5f, collarY - collarH * 0.5f),
            size = Size(collarW, collarH)
        )
        drawOval(
            color = outlineColor,
            topLeft = Offset(bx - collarW * 0.5f, collarY - collarH * 0.5f),
            size = Size(collarW, collarH),
            style = Stroke(width = strokeWidth)
        )

        // Đầu ngựa chiến điêu khắc 3D góc nhìn nghiêng 3/4 (uyển chuyển, sắc nét)
        val horsePath = Path().apply {
            moveTo(bx + w * 0.22f, collarY)
            // Bờm ngựa cong vươn ra sau rồi uốn lên đỉnh đầu
            cubicTo(
                bx + w * 0.34f, by - h * 0.48f,
                bx + w * 0.28f, by - h * 0.74f,
                bx + w * 0.08f, by - h * 0.94f
            )
            // Tai ngựa nhọn vươn cao
            lineTo(bx + w * 0.04f, by - h)
            lineTo(bx - w * 0.06f, by - h * 0.92f)
            // Trán ngựa dốc xuống mõm
            lineTo(bx - w * 0.18f, by - h * 0.84f)
            cubicTo(
                bx - w * 0.38f, by - h * 0.74f,
                bx - w * 0.48f, by - h * 0.68f,
                bx - w * 0.46f, by - h * 0.60f
            )
            // Mõm và hàm dưới bo tròn
            lineTo(bx - w * 0.32f, by - h * 0.54f)
            // Hầu họng và ức ngựa cong vào thân
            cubicTo(
                bx - w * 0.22f, by - h * 0.44f,
                bx - w * 0.24f, by - h * 0.36f,
                bx - w * 0.22f, collarY
            )
            close()
        }

        val horseBrush = if (isWhite) {
            Brush.linearGradient(
                colorStops = arrayOf(
                    0.0f to Color(0xFFFCF9F2),
                    0.35f to Color(0xFFE5DDD1),
                    0.70f to Color(0xFFB5ABA0),
                    1.0f to Color(0xFF7A6F62)
                ),
                start = Offset(bx - w * 0.48f, by - h),
                end = Offset(bx + w * 0.34f, collarY)
            )
        } else {
            Brush.linearGradient(
                colorStops = arrayOf(
                    0.0f to Color(0xFF554E4A),
                    0.35f to Color(0xFF332D2A),
                    0.70f to Color(0xFF161312),
                    1.0f to Color(0xFF050404)
                ),
                start = Offset(bx - w * 0.48f, by - h),
                end = Offset(bx + w * 0.34f, collarY)
            )
        }
        drawPath(horsePath, brush = horseBrush, style = Fill)
        drawPath(horsePath, color = outlineColor, style = Stroke(width = strokeWidth))

        // Tai thứ hai phía sau
        val ear2Path = Path().apply {
            moveTo(bx + w * 0.04f, by - h * 0.94f)
            lineTo(bx + w * 0.12f, by - h * 0.98f)
            lineTo(bx + w * 0.08f, by - h * 0.90f)
            close()
        }
        drawPath(ear2Path, brush = horseBrush, style = Fill)
        drawPath(ear2Path, color = outlineColor, style = Stroke(width = strokeWidth))

        // 3 Khía rãnh bờm ngựa
        val maneColor = if (isWhite) Color(0xFF6B5F52).copy(alpha = 0.55f) else Color(0xFFD6CFC7).copy(alpha = 0.45f)
        for (m in 1..3) {
            val my = by - h * (0.88f - m * 0.11f)
            val mx = bx + w * (0.16f + m * 0.04f)
            drawLine(
                color = maneColor,
                start = Offset(mx, my),
                end = Offset(mx - w * 0.10f, my + h * 0.05f),
                strokeWidth = 1.4f * depthScale,
                cap = StrokeCap.Round
            )
        }

        // Mắt ngựa điêu khắc tinh anh với đốm phản quang
        val eyeCenter = Offset(bx - w * 0.22f, by - h * 0.74f)
        drawCircle(color = outlineColor, radius = w * 0.045f, center = eyeCenter)
        val eyeHighlight = if (isWhite) Color.White else Color(0xFFE5DDD1)
        drawCircle(color = eyeHighlight, radius = w * 0.022f, center = Offset(eyeCenter.x - w * 0.015f, eyeCenter.y - w * 0.015f))

        // Khía cánh mũi ngựa
        val nostrilPath = Path().apply {
            moveTo(bx - w * 0.42f, by - h * 0.64f)
            lineTo(bx - w * 0.38f, by - h * 0.62f)
        }
        drawPath(nostrilPath, color = outlineColor, style = Stroke(width = strokeWidth * 0.9f, cap = StrokeCap.Round))
    }

    // 4. TƯỢNG (BISHOP)
    private fun DrawScope.drawTurnedBishop(
        bx: Float,
        by: Float,
        h: Float,
        w: Float,
        depthScale: Float,
        isWhite: Boolean
    ) {
        val strokeWidth = (1.8f * depthScale).coerceAtLeast(1.2f)
        val outlineColor = if (isWhite) Color(0xFF4A3F35) else Color(0xFF000000)

        // Chân đế 3 tầng
        drawTurnedBase(bx, by, w, depthScale, isWhite)

        // Thân thon quý phái
        val bodyStartY = by - w * 0.28f
        val collarY = by - h * 0.58f
        val bodyWBottom = w * 0.38f
        val bodyWWaist = w * 0.24f
        val bodyWTop = w * 0.30f

        val bodyPath = Path().apply {
            moveTo(bx - bodyWBottom * 0.5f, bodyStartY)
            cubicTo(
                bx - bodyWWaist * 0.5f, bodyStartY - (bodyStartY - collarY) * 0.45f,
                bx - bodyWWaist * 0.5f, bodyStartY - (bodyStartY - collarY) * 0.75f,
                bx - bodyWTop * 0.5f, collarY
            )
            lineTo(bx + bodyWTop * 0.5f, collarY)
            cubicTo(
                bx + bodyWWaist * 0.5f, bodyStartY - (bodyStartY - collarY) * 0.75f,
                bx + bodyWWaist * 0.5f, bodyStartY - (bodyStartY - collarY) * 0.45f,
                bx + bodyWBottom * 0.5f, bodyStartY
            )
            close()
        }
        val bodyBrush = createCylinderBrush(bx - bodyWBottom * 0.5f, bx + bodyWBottom * 0.5f, isWhite)
        drawPath(bodyPath, brush = bodyBrush, style = Fill)
        drawPath(bodyPath, color = outlineColor, style = Stroke(width = strokeWidth))

        // Vòng cổ tiện tròn
        val collarW = w * 0.44f
        val collarH = w * 0.15f
        drawOval(
            brush = bodyBrush,
            topLeft = Offset(bx - collarW * 0.5f, collarY - collarH * 0.5f),
            size = Size(collarW, collarH)
        )
        drawOval(
            color = outlineColor,
            topLeft = Offset(bx - collarW * 0.5f, collarY - collarH * 0.5f),
            size = Size(collarW, collarH),
            style = Stroke(width = strokeWidth)
        )

        // Vòng chỉ sáng ngà ở cổ quân Đen
        if (!isWhite) {
            drawOval(
                color = Color(0xFFD6CFC7).copy(alpha = 0.85f),
                topLeft = Offset(bx - collarW * 0.42f, collarY - collarH * 0.40f),
                size = Size(collarW * 0.84f, collarH * 0.80f),
                style = Stroke(width = 1.4f * depthScale)
            )
        }

        // Bầu mũ giáo mục tròn đầy đặn (Mitre)
        val mitreBottomY = collarY
        val mitreApexY = by - h * 0.93f
        val mitreMaxW = w * 0.46f

        val mitrePath = Path().apply {
            moveTo(bx - collarW * 0.44f, mitreBottomY)
            cubicTo(
                bx - mitreMaxW * 0.65f, mitreBottomY - (mitreBottomY - mitreApexY) * 0.35f,
                bx - mitreMaxW * 0.45f, mitreApexY + (mitreBottomY - mitreApexY) * 0.20f,
                bx, mitreApexY
            )
            cubicTo(
                bx + mitreMaxW * 0.45f, mitreApexY + (mitreBottomY - mitreApexY) * 0.20f,
                bx + mitreMaxW * 0.65f, mitreBottomY - (mitreBottomY - mitreApexY) * 0.35f,
                bx + collarW * 0.44f, mitreBottomY
            )
            close()
        }
        val mitreBrush = createCylinderBrush(bx - mitreMaxW * 0.65f, bx + mitreMaxW * 0.65f, isWhite)
        drawPath(mitrePath, brush = mitreBrush, style = Fill)
        drawPath(mitrePath, color = outlineColor, style = Stroke(width = strokeWidth))

        // Vết nứt chéo đặc trưng của mũ Tượng (Mitre Cleft)
        val cleftColor = if (isWhite) Color(0xFF4A3F35) else Color(0xFFD6CFC7).copy(alpha = 0.80f)
        drawLine(
            color = cleftColor,
            start = Offset(bx + w * 0.04f, by - h * 0.70f),
            end = Offset(bx + w * 0.22f, by - h * 0.84f),
            strokeWidth = 2.0f * depthScale,
            cap = StrokeCap.Round
        )

        // Núm cầu nhỏ (Finial) trên đỉnh mũ
        val finialR = w * 0.08f
        val finialCenter = Offset(bx, mitreApexY - finialR * 0.8f)
        val finialBrush = if (isWhite) {
            Brush.radialGradient(
                colors = listOf(Color(0xFFFCF9F2), Color(0xFFDFD7CB), Color(0xFF7A6E60)),
                center = Offset(finialCenter.x - finialR * 0.3f, finialCenter.y - finialR * 0.3f),
                radius = finialR * 1.3f
            )
        } else {
            Brush.radialGradient(
                colors = listOf(Color(0xFFD6CFC7), Color(0xFF554E4A), Color(0xFF141110)),
                center = Offset(finialCenter.x - finialR * 0.3f, finialCenter.y - finialR * 0.3f),
                radius = finialR * 1.3f
            )
        }
        drawCircle(brush = finialBrush, radius = finialR, center = finialCenter)
        drawCircle(color = outlineColor, radius = finialR, center = finialCenter, style = Stroke(width = strokeWidth))
    }

    // 5. HẬU (QUEEN)
    private fun DrawScope.drawTurnedQueen(
        bx: Float,
        by: Float,
        h: Float,
        w: Float,
        depthScale: Float,
        isWhite: Boolean
    ) {
        val strokeWidth = (1.8f * depthScale).coerceAtLeast(1.2f)
        val outlineColor = if (isWhite) Color(0xFF4A3F35) else Color(0xFF000000)

        // Chân đế 3 tầng
        drawTurnedBase(bx, by, w, depthScale, isWhite)

        // Thân cao kiêu sa
        val bodyStartY = by - w * 0.28f
        val collarY = by - h * 0.65f
        val bodyWBottom = w * 0.40f
        val bodyWWaist = w * 0.25f
        val bodyWTop = w * 0.32f

        val bodyPath = Path().apply {
            moveTo(bx - bodyWBottom * 0.5f, bodyStartY)
            cubicTo(
                bx - bodyWWaist * 0.5f, bodyStartY - (bodyStartY - collarY) * 0.45f,
                bx - bodyWWaist * 0.5f, bodyStartY - (bodyStartY - collarY) * 0.75f,
                bx - bodyWTop * 0.5f, collarY
            )
            lineTo(bx + bodyWTop * 0.5f, collarY)
            cubicTo(
                bx + bodyWWaist * 0.5f, bodyStartY - (bodyStartY - collarY) * 0.75f,
                bx + bodyWWaist * 0.5f, bodyStartY - (bodyStartY - collarY) * 0.45f,
                bx + bodyWBottom * 0.5f, bodyStartY
            )
            close()
        }
        val bodyBrush = createCylinderBrush(bx - bodyWBottom * 0.5f, bx + bodyWBottom * 0.5f, isWhite)
        drawPath(bodyPath, brush = bodyBrush, style = Fill)
        drawPath(bodyPath, color = outlineColor, style = Stroke(width = strokeWidth))

        // Vòng cổ kép tiện tròn
        val collarW = w * 0.46f
        val collarH = w * 0.16f
        drawOval(
            brush = bodyBrush,
            topLeft = Offset(bx - collarW * 0.5f, collarY - collarH * 0.5f),
            size = Size(collarW, collarH)
        )
        drawOval(
            color = outlineColor,
            topLeft = Offset(bx - collarW * 0.5f, collarY - collarH * 0.5f),
            size = Size(collarW, collarH),
            style = Stroke(width = strokeWidth)
        )

        // Vòng chỉ sáng ngà ở cổ quân Đen
        if (!isWhite) {
            drawOval(
                color = Color(0xFFD6CFC7).copy(alpha = 0.85f),
                topLeft = Offset(bx - collarW * 0.42f, collarY - collarH * 0.40f),
                size = Size(collarW * 0.84f, collarH * 0.80f),
                style = Stroke(width = 1.4f * depthScale)
            )
        }

        // Vương miện xòe nhiều đỉnh tròn (Scalloped Coronet)
        val crownTopY = by - h * 0.92f
        val crownRimW = w * 0.62f
        val crownRimH = w * 0.22f

        val coronetBody = Path().apply {
            moveTo(bx - collarW * 0.46f, collarY)
            lineTo(bx - crownRimW * 0.5f, crownTopY + crownRimH * 0.5f)
            lineTo(bx + crownRimW * 0.5f, crownTopY + crownRimH * 0.5f)
            lineTo(bx + collarW * 0.46f, collarY)
            close()
        }
        val crownBrush = createCylinderBrush(bx - crownRimW * 0.5f, bx + crownRimW * 0.5f, isWhite)
        drawPath(coronetBody, brush = crownBrush, style = Fill)
        drawPath(coronetBody, color = outlineColor, style = Stroke(width = strokeWidth))

        // Vành vương miện elip
        drawOval(
            brush = crownBrush,
            topLeft = Offset(bx - crownRimW * 0.5f, crownTopY),
            size = Size(crownRimW, crownRimH)
        )
        drawOval(
            color = outlineColor,
            topLeft = Offset(bx - crownRimW * 0.5f, crownTopY),
            size = Size(crownRimW, crownRimH),
            style = Stroke(width = strokeWidth)
        )

        // 7 Đỉnh ngọc tròn nhỏ dọc theo viền vương miện (Scalloped pearls)
        val pearlR = w * 0.045f
        for (i in 0..6) {
            val angle = PI.toFloat() * (0.05f + i * 0.15f)
            val px = bx - crownRimW * 0.48f * cos(angle)
            val py = crownTopY + crownRimH * 0.5f - crownRimH * 0.5f * sin(angle) - pearlR
            drawCircle(
                color = if (isWhite) Color(0xFFFCF9F2) else Color(0xFFD6CFC7),
                radius = pearlR,
                center = Offset(px, py)
            )
            drawCircle(color = outlineColor, radius = pearlR, center = Offset(px, py), style = Stroke(width = strokeWidth * 0.7f))
        }

        // Quả cầu ngọc lớn trung tâm ôm giữa vương miện
        val ballR = w * 0.11f
        val ballCenter = Offset(bx, crownTopY + crownRimH * 0.2f - ballR)
        val ballBrush = if (isWhite) {
            Brush.radialGradient(
                colors = listOf(Color(0xFFFCF9F2), Color(0xFFDFD7CB), Color(0xFF8A7F73)),
                center = Offset(ballCenter.x - ballR * 0.3f, ballCenter.y - ballR * 0.3f),
                radius = ballR * 1.3f
            )
        } else {
            Brush.radialGradient(
                colors = listOf(Color(0xFFE5DDD1), Color(0xFF4E4945), Color(0xFF141110)),
                center = Offset(ballCenter.x - ballR * 0.3f, ballCenter.y - ballR * 0.3f),
                radius = ballR * 1.3f
            )
        }
        drawCircle(brush = ballBrush, radius = ballR, center = ballCenter)
        drawCircle(color = outlineColor, radius = ballR, center = ballCenter, style = Stroke(width = strokeWidth))
    }

    // 6. VUA (KING)
    private fun DrawScope.drawTurnedKing(
        bx: Float,
        by: Float,
        h: Float,
        w: Float,
        depthScale: Float,
        isWhite: Boolean
    ) {
        val strokeWidth = (1.8f * depthScale).coerceAtLeast(1.2f)
        val outlineColor = if (isWhite) Color(0xFF4A3F35) else Color(0xFF000000)

        // Chân đế 3 tầng lớn bệ vệ
        drawTurnedBase(bx, by, w, depthScale, isWhite)

        // Thân áo choàng bề thế uy nghiêm
        val bodyStartY = by - w * 0.28f
        val collarY = by - h * 0.67f
        val bodyWBottom = w * 0.44f
        val bodyWWaist = w * 0.28f
        val bodyWTop = w * 0.36f

        val bodyPath = Path().apply {
            moveTo(bx - bodyWBottom * 0.5f, bodyStartY)
            cubicTo(
                bx - bodyWWaist * 0.5f, bodyStartY - (bodyStartY - collarY) * 0.45f,
                bx - bodyWWaist * 0.5f, bodyStartY - (bodyStartY - collarY) * 0.75f,
                bx - bodyWTop * 0.5f, collarY
            )
            lineTo(bx + bodyWTop * 0.5f, collarY)
            cubicTo(
                bx + bodyWWaist * 0.5f, bodyStartY - (bodyStartY - collarY) * 0.75f,
                bx + bodyWWaist * 0.5f, bodyStartY - (bodyStartY - collarY) * 0.45f,
                bx + bodyWBottom * 0.5f, bodyStartY
            )
            close()
        }
        val bodyBrush = createCylinderBrush(bx - bodyWBottom * 0.5f, bx + bodyWBottom * 0.5f, isWhite)
        drawPath(bodyPath, brush = bodyBrush, style = Fill)
        drawPath(bodyPath, color = outlineColor, style = Stroke(width = strokeWidth))

        // Vòng cổ kép tiện tròn
        val collarW = w * 0.50f
        val collarH = w * 0.17f
        drawOval(
            brush = bodyBrush,
            topLeft = Offset(bx - collarW * 0.5f, collarY - collarH * 0.5f),
            size = Size(collarW, collarH)
        )
        drawOval(
            color = outlineColor,
            topLeft = Offset(bx - collarW * 0.5f, collarY - collarH * 0.5f),
            size = Size(collarW, collarH),
            style = Stroke(width = strokeWidth)
        )

        // Vòng chỉ sáng ngà ở cổ quân Đen
        if (!isWhite) {
            drawOval(
                color = Color(0xFFD6CFC7).copy(alpha = 0.85f),
                topLeft = Offset(bx - collarW * 0.42f, collarY - collarH * 0.40f),
                size = Size(collarW * 0.84f, collarH * 0.80f),
                style = Stroke(width = 1.4f * depthScale)
            )
        }

        // Đỉnh vương miện tầng (Crown Dome)
        val domeBaseY = collarY
        val domeTopY = by - h * 0.87f
        val domeW = w * 0.52f
        val domeH = w * 0.20f

        val crownDome = Path().apply {
            moveTo(bx - collarW * 0.46f, domeBaseY)
            cubicTo(
                bx - domeW * 0.58f, domeBaseY - (domeBaseY - domeTopY) * 0.50f,
                bx - domeW * 0.38f, domeTopY,
                bx, domeTopY
            )
            cubicTo(
                bx + domeW * 0.38f, domeTopY,
                bx + domeW * 0.58f, domeBaseY - (domeBaseY - domeTopY) * 0.50f,
                bx + collarW * 0.46f, domeBaseY
            )
            close()
        }
        val domeBrush = createCylinderBrush(bx - domeW * 0.58f, bx + domeW * 0.58f, isWhite)
        drawPath(crownDome, brush = domeBrush, style = Fill)
        drawPath(crownDome, color = outlineColor, style = Stroke(width = strokeWidth))

        // Vành vương miện
        drawOval(
            brush = domeBrush,
            topLeft = Offset(bx - domeW * 0.5f, domeTopY - domeH * 0.4f),
            size = Size(domeW, domeH)
        )
        drawOval(
            color = outlineColor,
            topLeft = Offset(bx - domeW * 0.5f, domeTopY - domeH * 0.4f),
            size = Size(domeW, domeH),
            style = Stroke(width = strokeWidth)
        )

        // Vành chỉ sáng ngà trên vương miện vua Đen
        if (!isWhite) {
            drawOval(
                color = Color(0xFFD6CFC7).copy(alpha = 0.85f),
                topLeft = Offset(bx - domeW * 0.42f, domeTopY - domeH * 0.34f),
                size = Size(domeW * 0.84f, domeH * 0.68f),
                style = Stroke(width = 1.4f * depthScale)
            )
        }

        // CHỮ THẬP 3D (3D Cross) ĐỨNG TRÊN ĐỈNH ĐẦU ĐỨC VUA
        val crossBottomY = domeTopY - domeH * 0.2f
        val crossTopY = by - h
        val crossH = crossBottomY - crossTopY
        val crossBarW = w * 0.26f
        val crossThickness = (3.6f * depthScale).coerceAtLeast(2.4f)
        val crossBarY = crossTopY + crossH * 0.42f

        val crossColor = if (isWhite) Color(0xFFEDE6DA) else Color(0xFFD6CFC7)

        // Cột đứng chữ thập
        drawRoundRect(
            color = crossColor,
            topLeft = Offset(bx - crossThickness * 0.5f, crossTopY),
            size = Size(crossThickness, crossH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f, 1.5f)
        )
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(bx - crossThickness * 0.5f, crossTopY),
            size = Size(crossThickness, crossH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f, 1.5f),
            style = Stroke(width = strokeWidth * 0.8f)
        )

        // Thanh ngang chữ thập
        drawRoundRect(
            color = crossColor,
            topLeft = Offset(bx - crossBarW * 0.5f, crossBarY - crossThickness * 0.5f),
            size = Size(crossBarW, crossThickness),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f, 1.5f)
        )
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(bx - crossBarW * 0.5f, crossBarY - crossThickness * 0.5f),
            size = Size(crossBarW, crossThickness),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f, 1.5f),
            style = Stroke(width = strokeWidth * 0.8f)
        )

        // Vệt sáng cạnh trên và trái của chữ thập
        drawLine(
            color = Color.White.copy(alpha = 0.85f),
            start = Offset(bx - crossThickness * 0.5f, crossTopY),
            end = Offset(bx - crossThickness * 0.5f, crossBottomY),
            strokeWidth = 1.0f
        )
    }

    // =========================================================================
    // VẼ XE NHẬP THÀNH TRONG KHÔNG GIAN 3D
    // =========================================================================

    private fun DrawScope.drawCastlingRookIn3D(
        layout: PerspectiveLayout,
        move: ChessMove,
        progress: Float,
        isBoardFlipped: Boolean
    ) {
        val rookRank = move.from.row
        val rookStartCol = if (move.isKingsideCastle) 7 else 0
        val rookEndCol = if (move.isKingsideCastle) 5 else 3

        val rookFromSR = if (isBoardFlipped) 7 - rookRank else rookRank
        val rookFromSC = if (isBoardFlipped) 7 - rookStartCol else rookStartCol
        val rookToSR = if (isBoardFlipped) 7 - rookRank else rookRank
        val rookToSC = if (isBoardFlipped) 7 - rookEndCol else rookEndCol

        val quadRookFrom = layout.getQuad(rookFromSR, rookFromSC)
        val quadRookTo = layout.getQuad(rookToSR, rookToSC)

        val rookX = quadRookFrom.anchor.x + (quadRookTo.anchor.x - quadRookFrom.anchor.x) * progress
        val rookY = quadRookFrom.anchor.y + (quadRookTo.anchor.y - quadRookFrom.anchor.y) * progress
        val rookScale = quadRookFrom.depthScale + (quadRookTo.depthScale - quadRookFrom.depthScale) * progress

        drawSingleStauntonPiece3D(
            piece = Piece(PieceType.ROOK, move.piece.color),
            anchor = Offset(rookX, rookY),
            depthScale = rookScale,
            isSelected = false,
            elevationProgress = 0f
        )
    }
}
