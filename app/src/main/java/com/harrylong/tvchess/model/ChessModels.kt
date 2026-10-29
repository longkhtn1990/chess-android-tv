package com.harrylong.tvchess.model

enum class PieceColor(val vietnameseName: String) {
    WHITE("Trắng"),
    BLACK("Đen");

    fun opposite(): PieceColor = if (this == WHITE) BLACK else WHITE
}

enum class PieceType(
    val vietnameseName: String,
    val kidFriendlyName: String,
    val notationSymbol: String,
    val unicodeWhite: String,
    val unicodeBlack: String,
    val materialValue: Int,
    val displayPoints: Int
) {
    KING("Vua", "Đức Vua", "V", "♔", "♚", 20000, 0),
    QUEEN("Hậu", "Nữ Hoàng", "H", "♕", "♛", 900, 9),
    ROOK("Xe", "Xe Pháo Đài", "X", "♖", "♜", 500, 5),
    BISHOP("Tượng", "Quân Tượng", "T", "♗", "♝", 330, 3),
    KNIGHT("Mã", "Ngựa Chiến", "M", "♘", "♞", 320, 3),
    PAWN("Tốt", "Lính Tốt", "", "♙", "♟", 100, 1);

    companion object {
        val PROMOTION_OPTIONS = listOf(QUEEN, ROOK, BISHOP, KNIGHT)
    }
}

data class Piece(
    val type: PieceType,
    val color: PieceColor
) {
    val unicodeSymbol: String
        get() = if (color == PieceColor.WHITE) type.unicodeWhite else type.unicodeBlack
}

data class Position(
    val row: Int,
    val col: Int
) {
    fun isValid(): Boolean = row in 0..7 && col in 0..7

    /**
     * Tính toán vị trí ô kế tiếp khi di chuyển từng ô tự do trên bàn cờ 8x8.
     * Có tính đến trạng thái lật bàn cờ (isBoardFlipped) để các phím mũi tên / cần analog
     * luôn đi theo đúng hướng hiển thị trực quan trên màn hình TV của bé.
     */
    fun stepOnGrid(direction: DPadDirection, isBoardFlipped: Boolean): Position {
        val effectiveDr = if (isBoardFlipped) -direction.dr else direction.dr
        val effectiveDc = if (isBoardFlipped) -direction.dc else direction.dc
        val newRow = (row + effectiveDr).coerceIn(0, 7)
        val newCol = (col + effectiveDc).coerceIn(0, 7)
        return Position(newRow, newCol)
    }

    fun toAlgebraic(): String {
        val file = ('a' + col)
        val rank = (8 - row)
        return "$file$rank"
    }

    companion object {
        fun fromAlgebraic(str: String): Position? {
            if (str.length != 2) return null
            val col = str[0] - 'a'
            val row = 8 - (str[1] - '0')
            val pos = Position(row, col)
            return if (pos.isValid()) pos else null
        }
    }
}

data class CastlingRights(
    val whiteKingside: Boolean = true,
    val whiteQueenside: Boolean = true,
    val blackKingside: Boolean = true,
    val blackQueenside: Boolean = true
)

data class ChessMove(
    val from: Position,
    val to: Position,
    val piece: Piece,
    val capturedPiece: Piece? = null,
    val promotionPiece: PieceType? = null,
    val isKingsideCastle: Boolean = false,
    val isQueensideCastle: Boolean = false,
    val isEnPassant: Boolean = false,
    val notation: String = ""
)

enum class GameStatus {
    PLAYING,
    CHECK,
    CHECKMATE,
    STALEMATE,
    DRAW_FIFTY_MOVE,
    DRAW_INSUFFICIENT_MATERIAL,
    DRAW_REPETITION;

    val isGameOver: Boolean
        get() = this != PLAYING && this != CHECK
}

enum class Difficulty(
    val title: String,
    val monsterName: String,
    val subtitle: String,
    val searchDepth: Int,
    val starCount: Int
) {
    EASY("Khủng Long Nhí", "Khủng Long Nhí", "Hiền lành • Dễ chơi", 2, 1),
    MEDIUM("King Kong Con", "King Kong Con", "Thông minh • Vui nhộn", 3, 2),
    HARD("Godzilla Nhí", "Godzilla Nhí", "Siêu cấp • Thử thách", 4, 3);

    fun next(): Difficulty = when (this) {
        EASY -> MEDIUM
        MEDIUM -> HARD
        HARD -> EASY
    }
}

enum class BotMood(
    val bubbleEmoji: String,
    val shortCaption: String
) {
    IDLE_HAPPY("👋😊", "Tới lượt bé yêu đó!"),
    SURPRISED("😮⚡", "Oa! Bé định đi quân này hả?"),
    THINKING("🤔💭", "Hừm... Để tớ gãi đầu nghĩ đã!"),
    CRYING("😭💧", "Hu hu! Mất lính của tớ rồi!"),
    CLAPPING("👏🌟", "Hoan hô! Nước đi siêu quá!"),
    PROUD_LAUGH("😄✨", "Hì hì! Tớ đi xong rồi nhé!")
}

enum class FocusZone {
    PIECE_SELECTION,   // 4 phím mũi tên nhảy thông minh giữa các quân cờ hợp lệ của bé
    MOVE_SELECTION,    // 4 phím mũi tên nhảy thông minh giữa các ô đích hợp lệ của quân đang chọn
    SIDE_MENU,         // Đang ở bảng Nút Hình Ảnh bên phải (nhấn Back để mở/đóng)
    PROMOTION_DIALOG,  // Hộp thoại chọn quân Phong cấp (Hậu / Xe / Tượng / Mã)
    GAME_OVER_DIALOG   // Hộp thoại kết thúc ván đấu
}

enum class BoardNavigationMode(
    val vietnameseTitle: String,
    val badgeEmoji: String
) {
    SMART_JUMP("Nhảy thông minh", "⚡"),
    FREE_GRID("Đi từng ô", "🎯");

    fun next(): BoardNavigationMode = when (this) {
        SMART_JUMP -> FREE_GRID
        FREE_GRID -> SMART_JUMP
    }
}

enum class DPadDirection(val dr: Int, val dc: Int) {
    UP(-1, 0),
    DOWN(1, 0),
    LEFT(0, -1),
    RIGHT(0, 1)
}

data class GameState(
    val board: List<List<Piece?>>,
    val turn: PieceColor = PieceColor.WHITE,
    val castlingRights: CastlingRights = CastlingRights(),
    val enPassantTarget: Position? = null,
    val halfMoveClock: Int = 0,
    val fullMoveNumber: Int = 1,
    val positionHistory: List<String> = emptyList(),
    val moveHistory: List<ChessMove> = emptyList(),
    val capturedByWhite: List<Piece> = emptyList(),
    val capturedByBlack: List<Piece> = emptyList(),
    val status: GameStatus = GameStatus.PLAYING,
    val lastMove: ChessMove? = null
) {
    fun pieceAt(pos: Position): Piece? =
        if (pos.isValid()) board[pos.row][pos.col] else null

    fun pieceAt(row: Int, col: Int): Piece? =
        if (row in 0..7 && col in 0..7) board[row][col] else null
}
