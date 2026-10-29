package com.harrylong.tvchess

import com.harrylong.tvchess.engine.ChessAI
import com.harrylong.tvchess.engine.ChessEngine
import com.harrylong.tvchess.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.system.measureTimeMillis

class ChessAITest {

    private fun createEmptyBoard(): MutableList<MutableList<Piece?>> {
        return MutableList(8) { MutableList<Piece?>(8) { null } }
    }

    @Test
    fun mediumAndHard_findMateIn1_backRankMate() {
        // Thế cờ Back-rank mate:
        // Trắng: Vua ở g1 (row 7, col 6), Xe ở a7 (row 1, col 0)
        // Đen: Vua ở h8 (row 0, col 7), Tốt ở g7 (row 1, col 6), h7 (row 1, col 7)
        val board = createEmptyBoard()
        board[7][6] = Piece(PieceType.KING, PieceColor.WHITE)
        board[1][0] = Piece(PieceType.ROOK, PieceColor.WHITE)
        board[0][7] = Piece(PieceType.KING, PieceColor.BLACK)
        board[1][6] = Piece(PieceType.PAWN, PieceColor.BLACK)
        board[1][7] = Piece(PieceType.PAWN, PieceColor.BLACK)

        val state = GameState(
            board = board.map { it.toList() },
            turn = PieceColor.WHITE
        )

        val expectedTo = Position(0, 0) // Ra7 -> a8#

        // MEDIUM (King Kong) phải tìm ra nước chiếu hết trong 1 nước đi
        val moveMedium = ChessAI.findBestMove(state, Difficulty.MEDIUM)
        assertNotNull("MEDIUM phải tìm được nước đi", moveMedium)
        assertEquals("MEDIUM phải đi xe lên a8 để chiếu hết", expectedTo, moveMedium!!.to)
        val nextMedium = ChessEngine.applyMove(state, moveMedium)
        assertEquals("Nước đi phải dẫn tới CHECKMATE", GameStatus.CHECKMATE, nextMedium.status)

        // HARD (Godzilla) phải tìm ra nước chiếu hết trong 1 nước đi
        val moveHard = ChessAI.findBestMove(state, Difficulty.HARD)
        assertNotNull("HARD phải tìm được nước đi", moveHard)
        assertEquals("HARD phải đi xe lên a8 để chiếu hết", expectedTo, moveHard!!.to)
        val nextHard = ChessEngine.applyMove(state, moveHard)
        assertEquals("Nước đi phải dẫn tới CHECKMATE", GameStatus.CHECKMATE, nextHard.status)
    }

    @Test
    fun mediumAndHard_findMateIn1_queenMate() {
        // Thế cờ Hậu chiếu hết:
        // Trắng: Vua ở f6 (row 2, col 5), Hậu ở b7 (row 1, col 1)
        // Đen: Vua ở f8 (row 0, col 5)
        // Trắng đi Qb7 -> g7# hoặc f7# là chiếu hết
        val board = createEmptyBoard()
        board[2][5] = Piece(PieceType.KING, PieceColor.WHITE)
        board[1][1] = Piece(PieceType.QUEEN, PieceColor.WHITE)
        board[0][5] = Piece(PieceType.KING, PieceColor.BLACK)

        val state = GameState(
            board = board.map { it.toList() },
            turn = PieceColor.WHITE
        )

        for (diff in listOf(Difficulty.MEDIUM, Difficulty.HARD)) {
            val move = ChessAI.findBestMove(state, diff)
            assertNotNull("$diff phải tìm được nước đi", move)
            val nextState = ChessEngine.applyMove(state, move!!)
            assertEquals("$diff phải chọn nước chiếu hết", GameStatus.CHECKMATE, nextState.status)
        }
    }

    @Test
    fun hard_respectsTimeLimit() {
        val state = ChessEngine.createInitialState()
        val tightLimitMs = 120L

        val elapsed = measureTimeMillis {
            val move = ChessAI.findBestMove(state, Difficulty.HARD, timeLimitMs = tightLimitMs)
            assertNotNull(move)
            assertTrue(move in ChessEngine.getLegalMoves(state))
        }

        // Cho phép dung sai điều phối hệ thống tối đa 350ms
        assertTrue("HARD phải hoàn thành trong khoảng giới hạn thời gian (thực tế: ${elapsed}ms)", elapsed < 400L)
    }

    @Test
    fun allDifficulties_alwaysReturnLegalMoves() {
        val state = ChessEngine.createInitialState()
        val legalMoves = ChessEngine.getLegalMoves(state)

        for (diff in Difficulty.entries) {
            val move = ChessAI.findBestMove(state, diff)
            assertNotNull("AI phải trả về nước đi ở mức $diff", move)
            assertTrue("Nước đi của $diff phải thuộc danh sách hợp lệ FIDE", move in legalMoves)
        }
    }

    @Test
    fun applyFastMove_correctlyUpdatesCastlingRights() {
        val state = ChessEngine.createInitialState()

        // 1. Khi Vua Trắng di chuyển, mất toàn bộ quyền nhập thành của Trắng
        val whiteKingMove = ChessMove(
            from = Position(7, 4),
            to = Position(6, 4),
            piece = Piece(PieceType.KING, PieceColor.WHITE)
        )
        val stateAfterWhiteKing = ChessAI.applyFastMove(state, whiteKingMove)
        assertFalse(stateAfterWhiteKing.castlingRights.whiteKingside)
        assertFalse(stateAfterWhiteKing.castlingRights.whiteQueenside)
        assertTrue(stateAfterWhiteKing.castlingRights.blackKingside)
        assertTrue(stateAfterWhiteKing.castlingRights.blackQueenside)

        // 2. Khi Xe cánh Hậu Trắng di chuyển (a1 = row 7, col 0)
        val whiteRookQMove = ChessMove(
            from = Position(7, 0),
            to = Position(6, 0),
            piece = Piece(PieceType.ROOK, PieceColor.WHITE)
        )
        val stateAfterWhiteRookQ = ChessAI.applyFastMove(state, whiteRookQMove)
        assertFalse(stateAfterWhiteRookQ.castlingRights.whiteQueenside)
        assertTrue(stateAfterWhiteRookQ.castlingRights.whiteKingside)

        // 3. Khi Xe cánh Vua Trắng di chuyển (h1 = row 7, col 7)
        val whiteRookKMove = ChessMove(
            from = Position(7, 7),
            to = Position(6, 7),
            piece = Piece(PieceType.ROOK, PieceColor.WHITE)
        )
        val stateAfterWhiteRookK = ChessAI.applyFastMove(state, whiteRookKMove)
        assertFalse(stateAfterWhiteRookK.castlingRights.whiteKingside)
        assertTrue(stateAfterWhiteRookK.castlingRights.whiteQueenside)

        // 4. Khi ô a8 (row 0, col 0) của Đen bị ăn hoặc di chuyển
        val blackRookQMove = ChessMove(
            from = Position(0, 0),
            to = Position(1, 0),
            piece = Piece(PieceType.ROOK, PieceColor.BLACK)
        )
        val stateAfterBlackRookQ = ChessAI.applyFastMove(state, blackRookQMove)
        assertFalse(stateAfterBlackRookQ.castlingRights.blackQueenside)
        assertTrue(stateAfterBlackRookQ.castlingRights.blackKingside)
    }

    @Test
    fun applyFastMove_correctlySetsEnPassantTarget() {
        val state = ChessEngine.createInitialState()

        // Tốt trắng đi 2 ô: e2 (row 6, col 4) -> e4 (row 4, col 4)
        val pawnTwoSteps = ChessMove(
            from = Position(6, 4),
            to = Position(4, 4),
            piece = Piece(PieceType.PAWN, PieceColor.WHITE)
        )
        val stateAfterTwoSteps = ChessAI.applyFastMove(state, pawnTwoSteps)
        assertEquals(
            "Ô En Passant target phải được thiết lập tại e3 (row 5, col 4)",
            Position(5, 4),
            stateAfterTwoSteps.enPassantTarget
        )

        // Tốt trắng đi 1 ô: e2 (row 6, col 4) -> e3 (row 5, col 4)
        val pawnOneStep = ChessMove(
            from = Position(6, 4),
            to = Position(5, 4),
            piece = Piece(PieceType.PAWN, PieceColor.WHITE)
        )
        val stateAfterOneStep = ChessAI.applyFastMove(state, pawnOneStep)
        assertNull("Đi 1 ô không được tạo En Passant target", stateAfterOneStep.enPassantTarget)

        // Nước đi tiếp theo của quân khác phải xóa En Passant target
        val knightMove = ChessMove(
            from = Position(0, 1),
            to = Position(2, 2),
            piece = Piece(PieceType.KNIGHT, PieceColor.BLACK)
        )
        val stateAfterKnight = ChessAI.applyFastMove(stateAfterTwoSteps, knightMove)
        assertNull("Nước đi tiếp theo phải đặt enPassantTarget = null", stateAfterKnight.enPassantTarget)
    }

    @Test
    fun hardDifficulty_usesOpeningBookOnStartingPosition() {
        val state = ChessEngine.createInitialState()
        val move = ChessAI.findBestMove(state, Difficulty.HARD)
        assertNotNull(move)

        // Các nước mở đầu chuẩn trong sách khai cuộc: e4, d4, c4, Nf3
        val algebraic = move!!.toAlgebraicMove()
        val bookOpeners = listOf("e2e4", "d2d4", "c2c4", "g1f3")
        assertTrue("HARD phải chọn một nước đi trong sách khai cuộc chuẩn ($algebraic)", algebraic in bookOpeners)
    }

    private fun ChessMove.toAlgebraicMove(): String = "${from.toAlgebraic()}${to.toAlgebraic()}"
}
