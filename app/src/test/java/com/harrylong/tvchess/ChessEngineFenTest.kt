package com.harrylong.tvchess

import com.harrylong.tvchess.engine.ChessEngine
import com.harrylong.tvchess.model.GameStatus
import com.harrylong.tvchess.model.PieceColor
import com.harrylong.tvchess.model.PieceType
import com.harrylong.tvchess.model.Position
import org.junit.Assert.*
import org.junit.Test

class ChessEngineFenTest {

    @Test
    fun initialPosition_toFen_matchesStandardString() {
        val state = ChessEngine.createInitialState()
        val fen = ChessEngine.toFen(state)
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", fen)
    }

    @Test
    fun initialPosition_roundTrip_preservesState() {
        val initial = ChessEngine.createInitialState()
        val fen = ChessEngine.toFen(initial)
        val restored = ChessEngine.stateFromFen(fen)

        assertEquals(PieceColor.WHITE, restored.turn)
        assertTrue(restored.castlingRights.whiteKingside)
        assertTrue(restored.castlingRights.whiteQueenside)
        assertTrue(restored.castlingRights.blackKingside)
        assertTrue(restored.castlingRights.blackQueenside)
        assertNull(restored.enPassantTarget)
        assertEquals(0, restored.halfMoveClock)
        assertEquals(1, restored.fullMoveNumber)
        assertEquals(GameStatus.PLAYING, restored.status)

        // Bàn cờ khớp hoàn toàn
        for (r in 0..7) {
            for (c in 0..7) {
                assertEquals(initial.board[r][c], restored.board[r][c])
            }
        }
    }

    @Test
    fun fen_withEnPassant_andTurn_andMoveClocks() {
        val fen = "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2"
        val state = ChessEngine.stateFromFen(fen)

        assertEquals(PieceColor.WHITE, state.turn)
        assertEquals(Position.fromAlgebraic("c6"), state.enPassantTarget)
        assertEquals(0, state.halfMoveClock)
        assertEquals(2, state.fullMoveNumber)
        assertEquals(fen, ChessEngine.toFen(state))
    }

    @Test
    fun fen_detectsCheckmate() {
        // Fools mate position (Black queen h4 checks white king e1, no legal moves)
        val fen = "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3"
        val state = ChessEngine.stateFromFen(fen)

        assertEquals(GameStatus.CHECKMATE, state.status)
        assertEquals(0, ChessEngine.getLegalMoves(state).size)
    }

    @Test
    fun fen_detectsStalemate() {
        // Black to move, king at h8, white queen at f7, white king at g6
        val fen = "7k/5Q2/6K1/8/8/8/8/8 b - - 0 1"
        val state = ChessEngine.stateFromFen(fen)

        assertEquals(GameStatus.STALEMATE, state.status)
        assertEquals(0, ChessEngine.getLegalMoves(state).size)
    }

    @Test
    fun fen_detectsInsufficientMaterial_kingVsKing() {
        val fen = "8/8/4k3/8/8/4K3/8/8 w - - 0 1"
        val state = ChessEngine.stateFromFen(fen)

        assertEquals(GameStatus.DRAW_INSUFFICIENT_MATERIAL, state.status)
    }

    @Test
    fun fen_customCastlingRights() {
        val fen = "r3k2r/8/8/8/8/8/8/R3K2R w Kq - 5 10"
        val state = ChessEngine.stateFromFen(fen)

        assertTrue(state.castlingRights.whiteKingside)
        assertFalse(state.castlingRights.whiteQueenside)
        assertFalse(state.castlingRights.blackKingside)
        assertTrue(state.castlingRights.blackQueenside)
        assertEquals(fen, ChessEngine.toFen(state))
    }

    @Test
    fun fen_computesCapturedPiecesCorrectly() {
        // Bàn cờ thiếu 1 Hậu Đen và 2 Tốt Trắng
        val fen = "rnb1kbnr/pppppppp/8/8/8/8/PPPPPP2/RNBQKBNR w KQkq - 0 1"
        val state = ChessEngine.stateFromFen(fen)

        // Hậu đen mất -> Trắng đã ăn 1 Hậu Đen
        assertTrue(state.capturedByWhite.any { it.type == PieceType.QUEEN && it.color == PieceColor.BLACK })
        // 2 Tốt trắng mất -> Đen đã ăn 2 Tốt Trắng
        val capturedWhitePawns = state.capturedByBlack.filter { it.type == PieceType.PAWN && it.color == PieceColor.WHITE }
        assertEquals(2, capturedWhitePawns.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun fen_invalidRankCount_throwsException() {
        ChessEngine.stateFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun fen_invalidRankSquaresSum_throwsException() {
        ChessEngine.stateFromFen("rnbqkbnr/ppppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
    }
}
