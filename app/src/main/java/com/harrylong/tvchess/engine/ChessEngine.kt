package com.harrylong.tvchess.engine

import com.harrylong.tvchess.model.*
import kotlin.math.abs

object ChessEngine {

    fun createInitialState(): GameState {
        val board = MutableList(8) { MutableList<Piece?>(8) { null } }

        val backRank = listOf(
            PieceType.ROOK,
            PieceType.KNIGHT,
            PieceType.BISHOP,
            PieceType.QUEEN,
            PieceType.KING,
            PieceType.BISHOP,
            PieceType.KNIGHT,
            PieceType.ROOK
        )

        for (col in 0..7) {
            board[0][col] = Piece(backRank[col], PieceColor.BLACK)
            board[1][col] = Piece(PieceType.PAWN, PieceColor.BLACK)
            board[6][col] = Piece(PieceType.PAWN, PieceColor.WHITE)
            board[7][col] = Piece(backRank[col], PieceColor.WHITE)
        }

        val immutableBoard = board.map { it.toList() }
        val initialHash = computePositionKey(immutableBoard, PieceColor.WHITE, CastlingRights(), null)
        return GameState(
            board = immutableBoard,
            turn = PieceColor.WHITE,
            castlingRights = CastlingRights(),
            enPassantTarget = null,
            halfMoveClock = 0,
            fullMoveNumber = 1,
            positionHistory = listOf(initialHash)
        )
    }

    /**
     * Xuất trạng thái ván cờ ra chuỗi FEN (Forsyth–Edwards Notation) chuẩn quốc tế.
     * Ví dụ thế cờ ban đầu: "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
     */
    fun toFen(state: GameState): String {
        val sb = StringBuilder(80)

        // 1. Phân bố quân cờ trên 8 hàng (từ hàng 8 / row 0 xuống hàng 1 / row 7)
        for (r in 0..7) {
            var emptyCount = 0
            for (c in 0..7) {
                val p = state.board[r][c]
                if (p == null) {
                    emptyCount++
                } else {
                    if (emptyCount > 0) {
                        sb.append(emptyCount)
                        emptyCount = 0
                    }
                    val ch = when (p.type) {
                        PieceType.KING -> 'k'
                        PieceType.QUEEN -> 'q'
                        PieceType.ROOK -> 'r'
                        PieceType.BISHOP -> 'b'
                        PieceType.KNIGHT -> 'n'
                        PieceType.PAWN -> 'p'
                    }
                    sb.append(if (p.color == PieceColor.WHITE) ch.uppercaseChar() else ch)
                }
            }
            if (emptyCount > 0) {
                sb.append(emptyCount)
            }
            if (r < 7) {
                sb.append('/')
            }
        }

        // 2. Lượt đi: 'w' (Trắng) hoặc 'b' (Đen)
        sb.append(' ')
        sb.append(if (state.turn == PieceColor.WHITE) 'w' else 'b')

        // 3. Quyền nhập thành: K/Q/k/q hoặc '-'
        sb.append(' ')
        val cr = state.castlingRights
        val crSb = StringBuilder(4)
        if (cr.whiteKingside) crSb.append('K')
        if (cr.whiteQueenside) crSb.append('Q')
        if (cr.blackKingside) crSb.append('k')
        if (cr.blackQueenside) crSb.append('q')
        sb.append(if (crSb.isEmpty()) "-" else crSb.toString())

        // 4. Ô bắt tốt qua đường (En Passant): e.g. "e3" hoặc '-'
        sb.append(' ')
        sb.append(state.enPassantTarget?.toAlgebraic() ?: "-")

        // 5. Đồng hồ nửa nước (Halfmove clock)
        sb.append(' ')
        sb.append(state.halfMoveClock)

        // 6. Số nước đi toàn phần (Fullmove number)
        sb.append(' ')
        sb.append(state.fullMoveNumber)

        return sb.toString()
    }

    /**
     * Tải và phục hồi trạng thái ván cờ từ chuỗi FEN chuẩn.
     * Hỗ trợ cho các bài học cờ vua, thế cờ tuỳ chỉnh và câu đố chiếu tướng.
     */
    fun stateFromFen(fen: String): GameState {
        val tokens = fen.trim().split(Regex("\\s+"))
        require(tokens.isNotEmpty()) { "Chuỗi FEN không được rỗng" }

        val piecePlacement = tokens[0]
        val ranks = piecePlacement.split('/')
        require(ranks.size == 8) { "FEN phải có đúng 8 hàng, nhận được: ${ranks.size}" }

        val board = MutableList(8) { MutableList<Piece?>(8) { null } }
        for (r in 0..7) {
            val rankStr = ranks[r]
            var c = 0
            for (ch in rankStr) {
                if (ch.isDigit()) {
                    c += ch.digitToInt()
                } else {
                    require(c in 0..7) { "Hàng $r vượt quá 8 ô: $rankStr" }
                    val color = if (ch.isUpperCase()) PieceColor.WHITE else PieceColor.BLACK
                    val type = when (ch.lowercaseChar()) {
                        'k' -> PieceType.KING
                        'q' -> PieceType.QUEEN
                        'r' -> PieceType.ROOK
                        'b' -> PieceType.BISHOP
                        'n' -> PieceType.KNIGHT
                        'p' -> PieceType.PAWN
                        else -> throw IllegalArgumentException("Ký tự quân cờ FEN không hợp lệ: $ch")
                    }
                    board[r][c] = Piece(type, color)
                    c++
                }
            }
            require(c == 8) { "Hàng $r phải có tổng 8 ô, hiện tại: $c" }
        }

        val immutableBoard = board.map { it.toList() }

        val turn = if (tokens.size > 1) {
            if (tokens[1].lowercase() == "b") PieceColor.BLACK else PieceColor.WHITE
        } else {
            PieceColor.WHITE
        }

        val castlingRights = if (tokens.size > 2) {
            val cStr = tokens[2]
            CastlingRights(
                whiteKingside = cStr.contains('K'),
                whiteQueenside = cStr.contains('Q'),
                blackKingside = cStr.contains('k'),
                blackQueenside = cStr.contains('q')
            )
        } else {
            CastlingRights(false, false, false, false)
        }

        val enPassantTarget = if (tokens.size > 3 && tokens[3] != "-") {
            Position.fromAlgebraic(tokens[3])
        } else {
            null
        }

        val halfMoveClock = if (tokens.size > 4) {
            tokens[4].toIntOrNull() ?: 0
        } else {
            0
        }

        val fullMoveNumber = if (tokens.size > 5) {
            tokens[5].toIntOrNull()?.coerceAtLeast(1) ?: 1
        } else {
            1
        }

        // Tính toán các quân cờ bị ăn dựa trên số lượng quân chuẩn
        val initialCounts = mapOf(
            PieceType.PAWN to 8,
            PieceType.ROOK to 2,
            PieceType.KNIGHT to 2,
            PieceType.BISHOP to 2,
            PieceType.QUEEN to 1,
            PieceType.KING to 1
        )
        val whiteCounts = mutableMapOf<PieceType, Int>()
        val blackCounts = mutableMapOf<PieceType, Int>()
        for (r in 0..7) {
            for (c in 0..7) {
                val p = immutableBoard[r][c] ?: continue
                val map = if (p.color == PieceColor.WHITE) whiteCounts else blackCounts
                map[p.type] = (map[p.type] ?: 0) + 1
            }
        }
        val capturedByBlack = mutableListOf<Piece>()
        val capturedByWhite = mutableListOf<Piece>()
        for ((type, maxCount) in initialCounts) {
            val wMissing = maxCount - (whiteCounts[type] ?: 0)
            repeat(wMissing.coerceAtLeast(0)) {
                capturedByBlack.add(Piece(type, PieceColor.WHITE))
            }
            val bMissing = maxCount - (blackCounts[type] ?: 0)
            repeat(bMissing.coerceAtLeast(0)) {
                capturedByWhite.add(Piece(type, PieceColor.BLACK))
            }
        }

        val baseState = GameState(
            board = immutableBoard,
            turn = turn,
            castlingRights = castlingRights,
            enPassantTarget = enPassantTarget,
            halfMoveClock = halfMoveClock,
            fullMoveNumber = fullMoveNumber,
            capturedByWhite = capturedByWhite,
            capturedByBlack = capturedByBlack
        )

        val isCheck = isKingInCheck(immutableBoard, turn)
        val legalMoves = getLegalMoves(baseState, turn)
        val status = when {
            legalMoves.isEmpty() && isCheck -> GameStatus.CHECKMATE
            legalMoves.isEmpty() && !isCheck -> GameStatus.STALEMATE
            isCheck -> GameStatus.CHECK
            halfMoveClock >= 100 -> GameStatus.DRAW_FIFTY_MOVE
            isInsufficientMaterial(immutableBoard) -> GameStatus.DRAW_INSUFFICIENT_MATERIAL
            else -> GameStatus.PLAYING
        }

        val posKey = computePositionKey(immutableBoard, turn, castlingRights, enPassantTarget)

        return baseState.copy(
            status = status,
            positionHistory = listOf(posKey)
        )
    }

    fun findKingPosition(board: List<List<Piece?>>, color: PieceColor): Position? {
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c]
                if (p != null && p.color == color && p.type == PieceType.KING) {
                    return Position(r, c)
                }
            }
        }
        return null
    }

    fun isSquareAttacked(
        board: List<List<Piece?>>,
        target: Position,
        byColor: PieceColor
    ): Boolean {
        val tr = target.row
        val tc = target.col

        // 1. Kiểm tra Tốt (Pawn) tấn công
        val pawnRow = if (byColor == PieceColor.WHITE) tr + 1 else tr - 1
        if (pawnRow in 0..7) {
            for (dc in intArrayOf(-1, 1)) {
                val pc = tc + dc
                if (pc in 0..7) {
                    val p = board[pawnRow][pc]
                    if (p != null && p.color == byColor && p.type == PieceType.PAWN) return true
                }
            }
        }

        // 2. Kiểm tra Mã (Knight) tấn công
        val knightOffsets = arrayOf(
            intArrayOf(-2, -1), intArrayOf(-2, 1),
            intArrayOf(-1, -2), intArrayOf(-1, 2),
            intArrayOf(1, -2), intArrayOf(1, 2),
            intArrayOf(2, -1), intArrayOf(2, 1)
        )
        for (offset in knightOffsets) {
            val r = tr + offset[0]
            val c = tc + offset[1]
            if (r in 0..7 && c in 0..7) {
                val p = board[r][c]
                if (p != null && p.color == byColor && p.type == PieceType.KNIGHT) return true
            }
        }

        // 3. Kiểm tra Vua (King) đứng cạnh
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val r = tr + dr
                val c = tc + dc
                if (r in 0..7 && c in 0..7) {
                    val p = board[r][c]
                    if (p != null && p.color == byColor && p.type == PieceType.KING) return true
                }
            }
        }

        // 4. Kiểm tra Xe / Hậu (Ngang & Dọc)
        val orthoDirs = arrayOf(
            intArrayOf(-1, 0), intArrayOf(1, 0),
            intArrayOf(0, -1), intArrayOf(0, 1)
        )
        for (dir in orthoDirs) {
            var r = tr + dir[0]
            var c = tc + dir[1]
            while (r in 0..7 && c in 0..7) {
                val p = board[r][c]
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) {
                        return true
                    }
                    break
                }
                r += dir[0]
                c += dir[1]
            }
        }

        // 5. Kiểm tra Tượng / Hậu (Đường chéo)
        val diagDirs = arrayOf(
            intArrayOf(-1, -1), intArrayOf(-1, 1),
            intArrayOf(1, -1), intArrayOf(1, 1)
        )
        for (dir in diagDirs) {
            var r = tr + dir[0]
            var c = tc + dir[1]
            while (r in 0..7 && c in 0..7) {
                val p = board[r][c]
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) {
                        return true
                    }
                    break
                }
                r += dir[0]
                c += dir[1]
            }
        }

        return false
    }

    fun isKingInCheck(board: List<List<Piece?>>, color: PieceColor): Boolean {
        val kingPos = findKingPosition(board, color) ?: return false
        return isSquareAttacked(board, kingPos, color.opposite())
    }

    /**
     * Kiểm tra đường đi thẳng (ngang, dọc, chéo) giữa 2 ô có hoàn toàn trống rỗng không
     * theo đúng Điều 3.5 Luật Cờ Vua Quốc Tế FIDE (cấm đi xuyên qua bất kỳ quân cờ nào).
     */
    fun isPathClear(board: List<List<Piece?>>, from: Position, to: Position): Boolean {
        if (from == to) return false
        val dr = to.row - from.row
        val dc = to.col - from.col
        if (!(dr == 0 || dc == 0 || abs(dr) == abs(dc))) return false

        val stepR = dr.coerceIn(-1, 1)
        val stepC = dc.coerceIn(-1, 1)

        var currR = from.row + stepR
        var currC = from.col + stepC

        while (currR != to.row || currC != to.col) {
            if (currR !in 0..7 || currC !in 0..7) return false
            if (board[currR][currC] != null) return false
            currR += stepR
            currC += stepC
        }
        return true
    }

    /**
     * Kiểm định tính hợp lệ tuyệt đối của một nước đi theo Luật Cờ Vua Quốc Tế FIDE:
     * - Tọa độ hợp lệ và khác nhau.
     * - Quân cờ tại ô xuất phát đúng màu lượt đi và đúng chủng loại.
     * - Ô đích không chứa quân đồng minh và không được ăn trực tiếp Vua.
     * - Hình học di chuyển chuẩn xác và đường đi không bị cản (isPathClear).
     * - Chống lộ Vua / Ghim tuyệt đối (Pin - FIDE Điều 3.9).
     */
    fun isStrictFideLegalMove(state: GameState, move: ChessMove): Boolean {
        if (!move.from.isValid() || !move.to.isValid() || move.from == move.to) return false

        val piece = state.pieceAt(move.from) ?: return false
        if (piece.color != state.turn || piece.type != move.piece.type) return false

        val destPiece = state.pieceAt(move.to)
        if (destPiece != null && destPiece.color == piece.color) return false
        if (destPiece?.type == PieceType.KING) return false

        when (piece.type) {
            PieceType.ROOK -> {
                val isStraight = move.from.row == move.to.row || move.from.col == move.to.col
                if (!isStraight || !isPathClear(state.board, move.from, move.to)) return false
            }
            PieceType.BISHOP -> {
                val isDiagonal = abs(move.to.row - move.from.row) == abs(move.to.col - move.from.col)
                if (!isDiagonal || !isPathClear(state.board, move.from, move.to)) return false
            }
            PieceType.QUEEN -> {
                val isStraightOrDiag = move.from.row == move.to.row ||
                    move.from.col == move.to.col ||
                    abs(move.to.row - move.from.row) == abs(move.to.col - move.from.col)
                if (!isStraightOrDiag || !isPathClear(state.board, move.from, move.to)) return false
            }
            PieceType.KNIGHT -> {
                val dRow = abs(move.to.row - move.from.row)
                val dCol = abs(move.to.col - move.from.col)
                val isKnightJump = (dRow == 2 && dCol == 1) || (dRow == 1 && dCol == 2)
                if (!isKnightJump) return false
            }
            PieceType.PAWN -> {
                val dir = if (piece.color == PieceColor.WHITE) -1 else 1
                val startRow = if (piece.color == PieceColor.WHITE) 6 else 1

                if (move.from.col == move.to.col) {
                    if (destPiece != null || move.isEnPassant) return false
                    if (move.to.row == move.from.row + dir) {
                        // Đi thẳng 1 ô hợp lệ
                    } else if (move.from.row == startRow && move.to.row == move.from.row + 2 * dir) {
                        if (state.board[move.from.row + dir][move.from.col] != null) return false
                    } else {
                        return false
                    }
                } else if (abs(move.to.col - move.from.col) == 1 && move.to.row == move.from.row + dir) {
                    val hasEnemyDest = destPiece != null && destPiece.color != piece.color
                    val isLegalEnPassant = move.isEnPassant && state.enPassantTarget == move.to
                    if (!hasEnemyDest && !isLegalEnPassant) return false
                } else {
                    return false
                }
            }
            PieceType.KING -> {
                if (move.isKingsideCastle || move.isQueensideCastle) {
                    if (move.isKingsideCastle && move.isQueensideCastle) return false
                    val homeRow = if (piece.color == PieceColor.WHITE) 7 else 0
                    if (move.from.row != homeRow || move.from.col != 4) return false

                    val opponent = piece.color.opposite()
                    if (isSquareAttacked(state.board, move.from, opponent)) return false

                    if (move.isKingsideCastle) {
                        val canKingside = if (piece.color == PieceColor.WHITE) {
                            state.castlingRights.whiteKingside
                        } else {
                            state.castlingRights.blackKingside
                        }
                        if (!canKingside) return false
                        if (move.to != Position(homeRow, 6)) return false

                        val rook = state.board[homeRow][7]
                        if (rook == null || rook.color != piece.color || rook.type != PieceType.ROOK) return false

                        if (state.board[homeRow][5] != null || state.board[homeRow][6] != null) return false

                        if (isSquareAttacked(state.board, Position(homeRow, 5), opponent) ||
                            isSquareAttacked(state.board, Position(homeRow, 6), opponent)
                        ) return false
                    } else {
                        val canQueenside = if (piece.color == PieceColor.WHITE) {
                            state.castlingRights.whiteQueenside
                        } else {
                            state.castlingRights.blackQueenside
                        }
                        if (!canQueenside) return false
                        if (move.to != Position(homeRow, 2)) return false

                        val rook = state.board[homeRow][0]
                        if (rook == null || rook.color != piece.color || rook.type != PieceType.ROOK) return false

                        if (state.board[homeRow][1] != null ||
                            state.board[homeRow][2] != null ||
                            state.board[homeRow][3] != null
                        ) return false

                        if (isSquareAttacked(state.board, Position(homeRow, 3), opponent) ||
                            isSquareAttacked(state.board, Position(homeRow, 2), opponent)
                        ) return false
                    }
                } else {
                    if (abs(move.to.row - move.from.row) > 1 || abs(move.to.col - move.from.col) > 1) {
                        return false
                    }
                }
            }
        }

        val nextBoard = applyMoveToBoard(state.board, move)
        if (isKingInCheck(nextBoard, piece.color)) return false

        return true
    }

    fun getLegalMoves(state: GameState, color: PieceColor = state.turn): List<ChessMove> {
        val effectiveState = if (state.turn == color) state else state.copy(turn = color)
        val pseudoMoves = mutableListOf<ChessMove>()
        for (r in 0..7) {
            for (c in 0..7) {
                val piece = effectiveState.board[r][c]
                if (piece != null && piece.color == color) {
                    generatePseudoMovesForPiece(effectiveState, Position(r, c), piece, pseudoMoves)
                }
            }
        }

        return pseudoMoves.filter { move ->
            isStrictFideLegalMove(effectiveState, move)
        }
    }

    fun getLegalMovesForPiece(state: GameState, pos: Position): List<ChessMove> {
        val piece = state.pieceAt(pos) ?: return emptyList()
        if (piece.color != state.turn) return emptyList()
        val pseudoMoves = mutableListOf<ChessMove>()
        generatePseudoMovesForPiece(state, pos, piece, pseudoMoves)
        return pseudoMoves.filter { move ->
            isStrictFideLegalMove(state, move)
        }
    }

    fun getMovablePiecePositions(state: GameState, color: PieceColor = state.turn): List<Position> {
        val legalMoves = getLegalMoves(state, color)
        return legalMoves.map { it.from }.distinct()
    }

    private fun generatePseudoMovesForPiece(
        state: GameState,
        from: Position,
        piece: Piece,
        outMoves: MutableList<ChessMove>
    ) {
        val board = state.board
        val r = from.row
        val c = from.col
        val color = piece.color
        val opponent = color.opposite()

        when (piece.type) {
            PieceType.PAWN -> {
                val dir = if (color == PieceColor.WHITE) -1 else 1
                val startRow = if (color == PieceColor.WHITE) 6 else 1
                val promoRow = if (color == PieceColor.WHITE) 0 else 7

                // Đi thẳng 1 ô
                val oneStepRow = r + dir
                if (oneStepRow in 0..7 && board[oneStepRow][c] == null) {
                    val to = Position(oneStepRow, c)
                    if (oneStepRow == promoRow) {
                        for (promoType in PieceType.PROMOTION_OPTIONS) {
                            outMoves.add(
                                ChessMove(
                                    from = from,
                                    to = to,
                                    piece = piece,
                                    promotionPiece = promoType
                                )
                            )
                        }
                    } else {
                        outMoves.add(ChessMove(from = from, to = to, piece = piece))

                        // Đi thẳng 2 ô từ hàng xuất phát
                        val twoStepRow = r + dir * 2
                        if (r == startRow && board[twoStepRow][c] == null) {
                            outMoves.add(
                                ChessMove(
                                    from = from,
                                    to = Position(twoStepRow, c),
                                    piece = piece
                                )
                            )
                        }
                    }
                }

                // Ăn chéo (bao gồm cả Phong cấp và Bắt tốt qua đường - En Passant)
                for (dc in intArrayOf(-1, 1)) {
                    val capCol = c + dc
                    val capRow = r + dir
                    if (capRow in 0..7 && capCol in 0..7) {
                        val targetPos = Position(capRow, capCol)
                        val targetPiece = board[capRow][capCol]
                        if (targetPiece != null && targetPiece.color == opponent) {
                            if (capRow == promoRow) {
                                for (promoType in PieceType.PROMOTION_OPTIONS) {
                                    outMoves.add(
                                        ChessMove(
                                            from = from,
                                            to = targetPos,
                                            piece = piece,
                                            capturedPiece = targetPiece,
                                            promotionPiece = promoType
                                        )
                                    )
                                }
                            } else {
                                outMoves.add(
                                    ChessMove(
                                        from = from,
                                        to = targetPos,
                                        piece = piece,
                                        capturedPiece = targetPiece
                                    )
                                )
                            }
                        } else if (state.enPassantTarget == targetPos) {
                            val epCapturedPawn = board[r][capCol]
                            if (epCapturedPawn != null && epCapturedPawn.color == opponent && epCapturedPawn.type == PieceType.PAWN) {
                                outMoves.add(
                                    ChessMove(
                                        from = from,
                                        to = targetPos,
                                        piece = piece,
                                        capturedPiece = epCapturedPawn,
                                        isEnPassant = true
                                    )
                                )
                            }
                        }
                    }
                }
            }

            PieceType.KNIGHT -> {
                val offsets = arrayOf(
                    intArrayOf(-2, -1), intArrayOf(-2, 1),
                    intArrayOf(-1, -2), intArrayOf(-1, 2),
                    intArrayOf(1, -2), intArrayOf(1, 2),
                    intArrayOf(2, -1), intArrayOf(2, 1)
                )
                for (off in offsets) {
                    val nr = r + off[0]
                    val nc = c + off[1]
                    if (nr in 0..7 && nc in 0..7) {
                        val target = board[nr][nc]
                        if (target == null || target.color == opponent) {
                            outMoves.add(
                                ChessMove(
                                    from = from,
                                    to = Position(nr, nc),
                                    piece = piece,
                                    capturedPiece = target
                                )
                            )
                        }
                    }
                }
            }

            PieceType.BISHOP -> {
                val dirs = arrayOf(
                    intArrayOf(-1, -1), intArrayOf(-1, 1),
                    intArrayOf(1, -1), intArrayOf(1, 1)
                )
                addSlidingMoves(board, from, piece, dirs, outMoves)
            }

            PieceType.ROOK -> {
                val dirs = arrayOf(
                    intArrayOf(-1, 0), intArrayOf(1, 0),
                    intArrayOf(0, -1), intArrayOf(0, 1)
                )
                addSlidingMoves(board, from, piece, dirs, outMoves)
            }

            PieceType.QUEEN -> {
                val dirs = arrayOf(
                    intArrayOf(-1, 0), intArrayOf(1, 0),
                    intArrayOf(0, -1), intArrayOf(0, 1),
                    intArrayOf(-1, -1), intArrayOf(-1, 1),
                    intArrayOf(1, -1), intArrayOf(1, 1)
                )
                addSlidingMoves(board, from, piece, dirs, outMoves)
            }

            PieceType.KING -> {
                for (dr in -1..1) {
                    for (dc in -1..1) {
                        if (dr == 0 && dc == 0) continue
                        val nr = r + dr
                        val nc = c + dc
                        if (nr in 0..7 && nc in 0..7) {
                            val target = board[nr][nc]
                            if (target == null || target.color == opponent) {
                                outMoves.add(
                                    ChessMove(
                                        from = from,
                                        to = Position(nr, nc),
                                        piece = piece,
                                        capturedPiece = target
                                    )
                                )
                            }
                        }
                    }
                }

                // Kiểm tra Nhập thành (Castling)
                val homeRow = if (color == PieceColor.WHITE) 7 else 0
                if (r == homeRow && c == 4 && !isSquareAttacked(board, from, opponent)) {
                    val canKingside = if (color == PieceColor.WHITE) {
                        state.castlingRights.whiteKingside
                    } else {
                        state.castlingRights.blackKingside
                    }
                    if (canKingside) {
                        val rook = board[homeRow][7]
                        if (rook != null && rook.color == color && rook.type == PieceType.ROOK &&
                            board[homeRow][5] == null && board[homeRow][6] == null &&
                            !isSquareAttacked(board, Position(homeRow, 5), opponent) &&
                            !isSquareAttacked(board, Position(homeRow, 6), opponent)
                        ) {
                            outMoves.add(
                                ChessMove(
                                    from = from,
                                    to = Position(homeRow, 6),
                                    piece = piece,
                                    isKingsideCastle = true
                                )
                            )
                        }
                    }

                    val canQueenside = if (color == PieceColor.WHITE) {
                        state.castlingRights.whiteQueenside
                    } else {
                        state.castlingRights.blackQueenside
                    }
                    if (canQueenside) {
                        val rook = board[homeRow][0]
                        if (rook != null && rook.color == color && rook.type == PieceType.ROOK &&
                            board[homeRow][1] == null && board[homeRow][2] == null && board[homeRow][3] == null &&
                            !isSquareAttacked(board, Position(homeRow, 3), opponent) &&
                            !isSquareAttacked(board, Position(homeRow, 2), opponent)
                        ) {
                            outMoves.add(
                                ChessMove(
                                    from = from,
                                    to = Position(homeRow, 2),
                                    piece = piece,
                                    isQueensideCastle = true
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun addSlidingMoves(
        board: List<List<Piece?>>,
        from: Position,
        piece: Piece,
        dirs: Array<IntArray>,
        outMoves: MutableList<ChessMove>
    ) {
        val opponent = piece.color.opposite()
        for (dir in dirs) {
            var r = from.row + dir[0]
            var c = from.col + dir[1]
            while (r in 0..7 && c in 0..7) {
                val target = board[r][c]
                if (target == null) {
                    outMoves.add(
                        ChessMove(
                            from = from,
                            to = Position(r, c),
                            piece = piece
                        )
                    )
                } else {
                    if (target.color == opponent) {
                        outMoves.add(
                            ChessMove(
                                from = from,
                                to = Position(r, c),
                                piece = piece,
                                capturedPiece = target
                            )
                        )
                    }
                    break
                }
                r += dir[0]
                c += dir[1]
            }
        }
    }

    fun applyMoveToBoard(board: List<List<Piece?>>, move: ChessMove): List<List<Piece?>> {
        val mutable = board.map { it.toMutableList() }.toMutableList()
        mutable[move.from.row][move.from.col] = null

        if (move.isEnPassant) {
            mutable[move.from.row][move.to.col] = null
        }

        if (move.isKingsideCastle) {
            val row = move.from.row
            val rook = mutable[row][7]
            mutable[row][7] = null
            mutable[row][5] = rook
        } else if (move.isQueensideCastle) {
            val row = move.from.row
            val rook = mutable[row][0]
            mutable[row][0] = null
            mutable[row][3] = rook
        }

        val placedPiece = if (move.promotionPiece != null) {
            Piece(move.promotionPiece, move.piece.color)
        } else {
            move.piece
        }
        mutable[move.to.row][move.to.col] = placedPiece

        return mutable.map { it.toList() }
    }

    fun applyMove(state: GameState, move: ChessMove): GameState {
        if (!isStrictFideLegalMove(state, move)) {
            try {
                android.util.Log.w("ChessEngine", "Illegal move rejected by FIDE rules: $move")
            } catch (_: Throwable) {
                println("ChessEngine: Illegal move rejected by FIDE rules: $move")
            }
            return state
        }

        val nextBoard = applyMoveToBoard(state.board, move)
        val nextTurn = state.turn.opposite()

        // Cập nhật quyền nhập thành
        var cr = state.castlingRights
        if (move.piece.type == PieceType.KING) {
            cr = if (move.piece.color == PieceColor.WHITE) {
                cr.copy(whiteKingside = false, whiteQueenside = false)
            } else {
                cr.copy(blackKingside = false, blackQueenside = false)
            }
        }
        if (move.from == Position(7, 0) || move.to == Position(7, 0)) {
            cr = cr.copy(whiteQueenside = false)
        }
        if (move.from == Position(7, 7) || move.to == Position(7, 7)) {
            cr = cr.copy(whiteKingside = false)
        }
        if (move.from == Position(0, 0) || move.to == Position(0, 0)) {
            cr = cr.copy(blackQueenside = false)
        }
        if (move.from == Position(0, 7) || move.to == Position(0, 7)) {
            cr = cr.copy(blackKingside = false)
        }

        // Cập nhật ô bắt tốt qua đường (En Passant)
        val nextEnPassant = if (move.piece.type == PieceType.PAWN && abs(move.to.row - move.from.row) == 2) {
            Position((move.from.row + move.to.row) / 2, move.from.col)
        } else {
            null
        }

        // Cập nhật đồng hồ 50 nước và số lượt
        val nextHalfMove = if (move.piece.type == PieceType.PAWN || move.capturedPiece != null) {
            0
        } else {
            state.halfMoveClock + 1
        }
        val nextFullMove = if (state.turn == PieceColor.BLACK) {
            state.fullMoveNumber + 1
        } else {
            state.fullMoveNumber
        }

        // Cập nhật danh sách quân bị ăn
        val nextCapturedByWhite = state.capturedByWhite.toMutableList()
        val nextCapturedByBlack = state.capturedByBlack.toMutableList()
        if (move.capturedPiece != null) {
            if (state.turn == PieceColor.WHITE) {
                nextCapturedByWhite.add(move.capturedPiece)
                nextCapturedByWhite.sortByDescending { it.type.materialValue }
            } else {
                nextCapturedByBlack.add(move.capturedPiece)
                nextCapturedByBlack.sortByDescending { it.type.materialValue }
            }
        }

        val posKey = computePositionKey(nextBoard, nextTurn, cr, nextEnPassant)
        val nextPosHistory = state.positionHistory + posKey

        val tempState = state.copy(
            board = nextBoard,
            turn = nextTurn,
            castlingRights = cr,
            enPassantTarget = nextEnPassant,
            halfMoveClock = nextHalfMove,
            fullMoveNumber = nextFullMove,
            positionHistory = nextPosHistory,
            capturedByWhite = nextCapturedByWhite,
            capturedByBlack = nextCapturedByBlack
        )

        val opponentMoves = getLegalMoves(tempState, nextTurn)
        val inCheck = isKingInCheck(nextBoard, nextTurn)

        val status = when {
            opponentMoves.isEmpty() && inCheck -> GameStatus.CHECKMATE
            opponentMoves.isEmpty() && !inCheck -> GameStatus.STALEMATE
            nextHalfMove >= 100 -> GameStatus.DRAW_FIFTY_MOVE
            nextPosHistory.count { it == posKey } >= 3 -> GameStatus.DRAW_REPETITION
            isInsufficientMaterial(nextBoard) -> GameStatus.DRAW_INSUFFICIENT_MATERIAL
            inCheck -> GameStatus.CHECK
            else -> GameStatus.PLAYING
        }

        val notation = formatMoveNotation(move, inCheck, status == GameStatus.CHECKMATE)
        val recordedMove = move.copy(notation = notation)

        return tempState.copy(
            status = status,
            lastMove = recordedMove,
            moveHistory = state.moveHistory + recordedMove
        )
    }

    private fun formatMoveNotation(move: ChessMove, isCheck: Boolean, isCheckmate: Boolean): String {
        val suffix = when {
            isCheckmate -> "#"
            isCheck -> "+"
            else -> ""
        }
        if (move.isKingsideCastle) return "O-O$suffix"
        if (move.isQueensideCastle) return "O-O-O$suffix"

        val sb = StringBuilder()
        if (move.piece.type == PieceType.PAWN) {
            if (move.capturedPiece != null) {
                sb.append('a' + move.from.col)
                sb.append('x')
            }
            sb.append(move.to.toAlgebraic())
            if (move.promotionPiece != null) {
                sb.append('=').append(move.promotionPiece.notationSymbol)
            }
        } else {
            sb.append(move.piece.type.notationSymbol)
            if (move.capturedPiece != null) {
                sb.append('x')
            }
            sb.append(move.to.toAlgebraic())
        }
        sb.append(suffix)
        return sb.toString()
    }

    private fun isInsufficientMaterial(board: List<List<Piece?>>): Boolean {
        val pieces = mutableListOf<Pair<Piece, Position>>()
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c]
                if (p != null && p.type != PieceType.KING) {
                    pieces.add(p to Position(r, c))
                }
            }
        }
        if (pieces.isEmpty()) return true // Vua vs Vua
        if (pieces.size == 1) {
            val type = pieces[0].first.type
            return type == PieceType.BISHOP || type == PieceType.KNIGHT
        }
        if (pieces.size == 2) {
            val p1 = pieces[0]
            val p2 = pieces[1]
            if (p1.first.type == PieceType.BISHOP && p2.first.type == PieceType.BISHOP &&
                p1.first.color != p2.first.color
            ) {
                val color1 = (p1.second.row + p1.second.col) % 2
                val color2 = (p2.second.row + p2.second.col) % 2
                return color1 == color2
            }
        }
        return false
    }

    fun computePositionKey(
        board: List<List<Piece?>>,
        turn: PieceColor,
        cr: CastlingRights,
        ep: Position?
    ): String {
        val sb = StringBuilder(90)
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c]
                if (p == null) {
                    sb.append('.')
                } else {
                    val ch = when (p.type) {
                        PieceType.KING -> 'k'
                        PieceType.QUEEN -> 'q'
                        PieceType.ROOK -> 'r'
                        PieceType.BISHOP -> 'b'
                        PieceType.KNIGHT -> 'n'
                        PieceType.PAWN -> 'p'
                    }
                    sb.append(if (p.color == PieceColor.WHITE) ch.uppercaseChar() else ch)
                }
            }
        }
        sb.append('|').append(if (turn == PieceColor.WHITE) 'W' else 'B')
        sb.append('|')
        if (cr.whiteKingside) sb.append('K')
        if (cr.whiteQueenside) sb.append('Q')
        if (cr.blackKingside) sb.append('k')
        if (cr.blackQueenside) sb.append('q')
        sb.append('|').append(ep?.toAlgebraic() ?: "-")
        return sb.toString()
    }

    /**
     * Thuật toán Nhảy Thông Minh (Smart Jump) cho Remote Xiaomi (D-Pad 4 hướng):
     * - Khi chưa chọn quân: chỉ nhảy giữa các quân cờ có nước đi hợp lệ của người chơi.
     * - Khi đã chọn quân: chỉ nhảy giữa các ô nước đi hợp lệ của quân đó.
     * - Tự động tính toán theo góc nhìn màn hình (nếu người chơi cầm quân Đen và bàn cờ xoay 180 độ).
     */
    fun findNextSmartJump(
        current: Position,
        candidates: List<Position>,
        direction: DPadDirection,
        isBoardFlipped: Boolean
    ): Position {
        val distinctCandidates = candidates.distinct()
        if (distinctCandidates.isEmpty()) return current
        if (distinctCandidates.size == 1) return distinctCandidates.first()

        fun toScreenCoord(pos: Position): Pair<Int, Int> {
            return if (isBoardFlipped) {
                (7 - pos.row) to (7 - pos.col)
            } else {
                pos.row to pos.col
            }
        }

        val (r0, c0) = toScreenCoord(current)
        val dr = direction.dr
        val dc = direction.dc

        val others = distinctCandidates.filter { it != current }
        if (others.isEmpty()) return current

        // Bước 1: Tìm các ô nằm đúng hướng bấm phím (proj > 0) và trong vùng hình nón ưu tiên
        var bestConeCandidate: Position? = null
        var bestConeScore = Double.MAX_VALUE

        // Bước 2: Tìm các ô nằm ở nửa mặt phẳng hướng bấm phím (proj > 0) nếu không có ô nào trong hình nón hẹp
        var bestHalfPlaneCandidate: Position? = null
        var bestHalfPlaneScore = Double.MAX_VALUE

        for (cand in others) {
            val (r, c) = toScreenCoord(cand)
            val dy = r - r0
            val dx = c - c0

            val proj = dy * dr + dx * dc
            val perp = abs(dy * dc - dx * dr)

            if (proj > 0) {
                // Hình nón mở rộng: perp <= proj * 1.5
                if (perp <= proj * 1.5) {
                    val score = proj * 1.0 + perp * 1.85 + (r * 0.001 + c * 0.0001)
                    if (score < bestConeScore) {
                        bestConeScore = score
                        bestConeCandidate = cand
                    }
                }

                val halfPlaneScore = proj * 1.4 + perp * 1.1 + (r * 0.001 + c * 0.0001)
                if (halfPlaneScore < bestHalfPlaneScore) {
                    bestHalfPlaneScore = halfPlaneScore
                    bestHalfPlaneCandidate = cand
                }
            }
        }

        if (bestConeCandidate != null) return bestConeCandidate
        if (bestHalfPlaneCandidate != null) return bestHalfPlaneCandidate

        // Bước 3: Wrap-around (vòng sang phía đối diện nếu đang ở rìa ngoài cùng của hướng bấm)
        var bestWrapCandidate: Position = others.first()
        var bestWrapScore = Double.MAX_VALUE
        for (cand in others) {
            val (r, c) = toScreenCoord(cand)
            val dy = r - r0
            val dx = c - c0
            val proj = dy * dr + dx * dc // proj <= 0
            val perp = abs(dy * dc - dx * dr)
            // Ưu tiên ô ở xa nhất về phía ngược lại (tức đầu bên kia của hàng/cột) và gần trục nhất
            val wrapScore = proj * 1.0 + perp * 2.0 + (r * 0.001 + c * 0.0001)
            if (wrapScore < bestWrapScore) {
                bestWrapScore = wrapScore
                bestWrapCandidate = cand
            }
        }
        return bestWrapCandidate
    }
}
