package com.harrylong.tvchess.learn

import com.harrylong.tvchess.model.ChessMove
import com.harrylong.tvchess.model.PieceType
import com.harrylong.tvchess.model.Position

/**
 * Mục tiêu mà bé cần đạt được trong các bước thực hành (Practice).
 */
sealed interface PracticeGoal {
    /**
     * Bé cần thực hiện chính xác một nước đi từ ô [from] đến ô [to],
     * kèm quân phong cấp [promotionPiece] nếu có (ví dụ Tốt phong Hậu).
     */
    data class SpecificMove(
        val from: Position,
        val to: Position,
        val promotionPiece: PieceType? = null
    ) : PracticeGoal

    /**
     * Bé điều khiển quân cờ tại ô [from] đi đến bất kỳ ô nào trong tập [targetSquares].
     */
    data class MovePieceToTarget(
        val from: Position,
        val targetSquares: Set<Position>
    ) : PracticeGoal

    /**
     * Thử thách "Ăn hết sao" (hoặc bắt hết mục tiêu):
     * Bé di chuyển quân cờ thu thập tất cả các ô sao [targetSquares].
     * Mỗi lần bước vào ô có sao, ô đó được tính là đã thu thập thành công.
     */
    data class CaptureAllTargets(
        val targetSquares: Set<Position>
    ) : PracticeGoal

    /**
     * Bé cần tung ra một nước đi hợp lệ tạo thành Chiếu Hết (Checkmate) đối phương!
     */
    object GiveCheckmate : PracticeGoal

    /**
     * Vua của bé đang bị chiếu, bé cần thực hiện một nước đi hợp lệ để thoát chiếu
     * (Chạy Vua, Chặn đường chiếu, hoặc Ăn quân đang chiếu).
     */
    object EscapeCheck : PracticeGoal
}

/**
 * Loại bước trong bài học: Giải thích (Explain), Thực hành (Practice), Câu đố (Quiz), Tốt nghiệp (Graduation).
 */
sealed interface LessonStep {
    val title: String
    val kidText: String
    val voiceKey: String
}

/**
 * Bước giải thích trực quan:
 * Hiển thị thế cờ từ [fen], tô sáng các ô [highlightedSquares], vẽ các mũi tên dẫn hướng [arrows],
 * hoặc chạy hoạt hình mẫu nước đi [demoMove].
 */
data class ExplainStep(
    override val title: String,
    override val kidText: String,
    override val voiceKey: String,
    val fen: String = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    val highlightedSquares: List<Position> = emptyList(),
    val arrows: List<Pair<Position, Position>> = emptyList(),
    val demoMove: Pair<Position, Position>? = null,
    val detailText: String? = null
) : LessonStep

/**
 * Bước thực hành tương tác:
 * Bé tự tay di chuyển quân cờ trên bàn [fen] để hoàn thành [goal].
 */
data class PracticeStep(
    override val title: String,
    override val kidText: String,
    override val voiceKey: String,
    val fen: String,
    val goal: PracticeGoal,
    val hint: String? = null,
    val targetSquares: Set<Position> = emptySet(),
    val recommendedFrom: Position? = null,
    val recommendedTo: Position? = null
) : LessonStep

/**
 * Bước câu đố chọn ô:
 * Bé chạm vào tất cả các ô mà quân cờ tại [piecePos] có thể di chuyển tới [correctSquares].
 */
data class QuizStep(
    override val title: String,
    override val kidText: String,
    override val voiceKey: String,
    val fen: String,
    val piecePos: Position,
    val correctSquares: Set<Position>,
    val hint: String? = null
) : LessonStep

/**
 * Bước tốt nghiệp chúc mừng bé Đậu:
 * Tuyên dương hoàn thành khóa học và mở nút bắt đầu ván cờ với Khủng Long Nhí.
 */
data class GraduationStep(
    override val title: String,
    override val kidText: String,
    override val voiceKey: String,
    val celebrationText: String
) : LessonStep

/**
 * Cấu trúc một bài học cờ vua hoàn chỉnh.
 */
data class Lesson(
    val id: String,
    val orderNumber: Int,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val steps: List<LessonStep>
)
