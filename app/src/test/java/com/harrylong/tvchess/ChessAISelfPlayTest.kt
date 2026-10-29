package com.harrylong.tvchess

import com.harrylong.tvchess.engine.ChessAI
import com.harrylong.tvchess.engine.ChessEngine
import com.harrylong.tvchess.model.Difficulty
import com.harrylong.tvchess.model.GameStatus
import com.harrylong.tvchess.model.PieceColor
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureNanoTime

class ChessAISelfPlayTest {

    data class GameResult(
        val winnerDiff: Difficulty?,
        val reason: String,
        val totalMoves: Int,
        val finalStatus: GameStatus
    )

    data class MatchStats(
        var strongWins: Int = 0,
        var weakWins: Int = 0,
        var draws: Int = 0,
        var totalTimeStrongNs: Long = 0,
        var countMovesStrong: Int = 0,
        var totalTimeWeakNs: Long = 0,
        var countMovesWeak: Int = 0,
        var totalGodzillaDepth: Int = 0,
        var countGodzillaDepthMoves: Int = 0
    ) {
        val avgGodzillaDepth: Double
            get() = if (countGodzillaDepthMoves > 0) totalGodzillaDepth.toDouble() / countGodzillaDepthMoves else 0.0
    }

    private fun playOneGame(
        whiteDiff: Difficulty,
        blackDiff: Difficulty,
        maxFullMoves: Int = 35,
        stats: MatchStats,
        strongDiff: Difficulty
    ): GameResult {
        var state = ChessEngine.createInitialState()

        while (!state.status.isGameOver && state.fullMoveNumber <= maxFullMoves) {
            val currentTurn = state.turn
            val currentDiff = if (currentTurn == PieceColor.WHITE) whiteDiff else blackDiff

            var move: com.harrylong.tvchess.model.ChessMove?
            val elapsedNs = measureNanoTime {
                move = ChessAI.findBestMove(state, currentDiff)
            }

            if (currentDiff == strongDiff) {
                stats.totalTimeStrongNs += elapsedNs
                stats.countMovesStrong++
            } else {
                stats.totalTimeWeakNs += elapsedNs
                stats.countMovesWeak++
            }

            if (currentDiff == Difficulty.HARD && ChessAI.lastSearchedDepth > 0) {
                stats.totalGodzillaDepth += ChessAI.lastSearchedDepth
                stats.countGodzillaDepthMoves++
            }

            if (move == null) break
            state = ChessEngine.applyMove(state, move!!)
        }

        return when {
            state.status == GameStatus.CHECKMATE -> {
                val winnerColor = state.turn.opposite()
                val winnerDiff = if (winnerColor == PieceColor.WHITE) whiteDiff else blackDiff
                GameResult(winnerDiff, "CHECKMATE", state.fullMoveNumber, state.status)
            }
            state.status.isGameOver -> {
                GameResult(null, state.status.name, state.fullMoveNumber, state.status)
            }
            else -> {
                val evalWhite = ChessAI.evaluateBoard(state.board, PieceColor.WHITE)
                when {
                    evalWhite > 200 -> GameResult(whiteDiff, "EVAL_ADVANTAGE ($evalWhite)", state.fullMoveNumber, state.status)
                    evalWhite < -200 -> GameResult(blackDiff, "EVAL_ADVANTAGE ($evalWhite)", state.fullMoveNumber, state.status)
                    else -> GameResult(null, "DRAW_CAPPED ($evalWhite)", state.fullMoveNumber, state.status)
                }
            }
        }
    }

    private fun runMatchSeries(
        strongDiff: Difficulty,
        weakDiff: Difficulty,
        totalGames: Int = 20,
        maxFullMoves: Int = 35
    ): MatchStats {
        val stats = MatchStats()

        for (i in 1..totalGames) {
            val strongIsWhite = (i % 2 != 0)
            val whiteDiff = if (strongIsWhite) strongDiff else weakDiff
            val blackDiff = if (strongIsWhite) weakDiff else strongDiff

            val res = playOneGame(whiteDiff, blackDiff, maxFullMoves, stats, strongDiff)

            when (res.winnerDiff) {
                strongDiff -> stats.strongWins++
                weakDiff -> stats.weakWins++
                else -> stats.draws++
            }
        }

        return stats
    }

    @Test
    @org.junit.Ignore("AI Self-play benchmark HARD vs MEDIUM (20 games). Remove @Ignore to benchmark.")
    fun benchmarkSelfPlay_hardVsMedium() {
        println("\n=======================================================")
        println("=== TỰ ĐẤU: 20 VÁN GODZILLA (KHÓ) vs KING KONG (TB) ===")
        println("=======================================================")

        val hardVsMed = runMatchSeries(Difficulty.HARD, Difficulty.MEDIUM, totalGames = 20, maxFullMoves = 35)

        val avgTimeHard = if (hardVsMed.countMovesStrong > 0) (hardVsMed.totalTimeStrongNs / hardVsMed.countMovesStrong) / 1_000_000.0 else 0.0
        val avgTimeMed = if (hardVsMed.countMovesWeak > 0) (hardVsMed.totalTimeWeakNs / hardVsMed.countMovesWeak) / 1_000_000.0 else 0.0
        val totalScoreHard = hardVsMed.strongWins + 0.5 * hardVsMed.draws
        val winRateHard = (totalScoreHard / 20.0) * 100.0

        println("KẾT QUẢ GODZILLA vs KING KONG:")
        println(" - Godzilla thắng: ${hardVsMed.strongWins} / 20")
        println(" - King Kong thắng: ${hardVsMed.weakWins} / 20")
        println(" - Hòa: ${hardVsMed.draws} / 20")
        println(" - Tỉ lệ điểm của Godzilla: %.1f%% (Thắng: %.1f / 20)".format(winRateHard, totalScoreHard))
        println(" - Độ sâu tìm kiếm trung bình của Godzilla: %.2f".format(hardVsMed.avgGodzillaDepth))
        println(" - Thời gian nghĩ trung bình Godzilla: %.2f ms / nước".format(avgTimeHard))
        println(" - Thời gian nghĩ trung bình King Kong: %.2f ms / nước".format(avgTimeMed))

        assertTrue(
            "Godzilla (HARD) phải thắng rõ rệt King Kong (MEDIUM) với tỉ lệ điểm > 65% (hiện tại: ${winRateHard}%)",
            winRateHard >= 65.0
        )
    }

    @Test
    @org.junit.Ignore("AI Self-play benchmark (40 games). Run manually or remove @Ignore to benchmark.")
    fun benchmarkSelfPlay_assertStrongerWinsMore() {
        println("\n=======================================================")
        println("=== TỰ ĐẤU ĐÁNH GIÁ SỨC MẠNH CỜ VUA (AI SELF-PLAY) ===")
        println("=======================================================")

        // 1. HARD (Godzilla) vs EASY (Khủng Long) - 20 ván
        println("\n>>> ĐANG CHẠY: 20 VÁN GODZILLA (KHÓ) vs KHỦNG LONG (DỄ)...")
        val hardVsEasy = runMatchSeries(Difficulty.HARD, Difficulty.EASY, totalGames = 20, maxFullMoves = 35)

        val avgTimeHard = if (hardVsEasy.countMovesStrong > 0) (hardVsEasy.totalTimeStrongNs / hardVsEasy.countMovesStrong) / 1_000_000.0 else 0.0
        val avgTimeEasy = if (hardVsEasy.countMovesWeak > 0) (hardVsEasy.totalTimeWeakNs / hardVsEasy.countMovesWeak) / 1_000_000.0 else 0.0

        println("KẾT QUẢ GODZILLA vs KHỦNG LONG:")
        println(" - Godzilla thắng: ${hardVsEasy.strongWins} / 20")
        println(" - Khủng Long thắng: ${hardVsEasy.weakWins} / 20")
        println(" - Hòa: ${hardVsEasy.draws} / 20")
        println(" - Độ sâu tìm kiếm trung bình của Godzilla: %.2f".format(hardVsEasy.avgGodzillaDepth))
        println(" - Thời gian nghĩ trung bình Godzilla: %.2f ms / nước".format(avgTimeHard))
        println(" - Thời gian nghĩ trung bình Khủng Long: %.2f ms / nước".format(avgTimeEasy))

        assertTrue(
            "Godzilla (HARD) phải thắng áp đảo Khủng Long (EASY)",
            hardVsEasy.strongWins > hardVsEasy.weakWins
        )

        // 2. MEDIUM (King Kong) vs EASY (Khủng Long) - 20 ván
        println("\n>>> ĐANG CHẠY: 20 VÁN KING KONG (TRUNG BÌNH) vs KHỦNG LONG (DỄ)...")
        val medVsEasy = runMatchSeries(Difficulty.MEDIUM, Difficulty.EASY, totalGames = 20, maxFullMoves = 35)

        val avgTimeMed = if (medVsEasy.countMovesStrong > 0) (medVsEasy.totalTimeStrongNs / medVsEasy.countMovesStrong) / 1_000_000.0 else 0.0

        println("KẾT QUẢ KING KONG vs KHỦNG LONG:")
        println(" - King Kong thắng: ${medVsEasy.strongWins} / 20")
        println(" - Khủng Long thắng: ${medVsEasy.weakWins} / 20")
        println(" - Hòa: ${medVsEasy.draws} / 20")
        println(" - Thời gian nghĩ trung bình King Kong: %.2f ms / nước".format(avgTimeMed))

        assertTrue(
            "King Kong (MEDIUM) phải thắng Khủng Long (EASY)",
            medVsEasy.strongWins > medVsEasy.weakWins
        )
    }
}
