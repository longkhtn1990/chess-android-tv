package com.harrylong.tvchess.learn

import com.harrylong.tvchess.engine.ChessEngine
import com.harrylong.tvchess.model.ChessMove
import com.harrylong.tvchess.model.GameStatus
import org.junit.Assert.*
import org.junit.Test

class LessonEngineTest {

    @Test
    fun testAll12LessonsExistAndOrdered() {
        val lessons = LessonRepository.lessons
        assertEquals(12, lessons.size)
        for (i in lessons.indices) {
            assertEquals(i + 1, lessons[i].orderNumber)
            assertTrue(lessons[i].steps.isNotEmpty())
        }
    }

    @Test
    fun testAllFensAreValid() {
        for (lesson in LessonRepository.lessons) {
            for ((stepIdx, step) in lesson.steps.withIndex()) {
                when (step) {
                    is ExplainStep -> {
                        val state = ChessEngine.stateFromFen(step.fen)
                        assertNotNull("Lesson ${lesson.id} step $stepIdx FEN failed", state)
                    }
                    is PracticeStep -> {
                        val state = ChessEngine.stateFromFen(step.fen)
                        assertNotNull("Lesson ${lesson.id} step $stepIdx FEN failed", state)
                    }
                    is QuizStep -> {
                        val state = ChessEngine.stateFromFen(step.fen)
                        assertNotNull("Lesson ${lesson.id} step $stepIdx FEN failed", state)
                    }
                    is GraduationStep -> {
                        // No FEN
                    }
                }
            }
        }
    }

    @Test
    fun testAllPracticeGoalsAreAchievableWithLegalMoves() {
        for (lesson in LessonRepository.lessons) {
            for ((stepIdx, step) in lesson.steps.withIndex()) {
                if (step is PracticeStep) {
                    val state = ChessEngine.stateFromFen(step.fen)
                    val legalMoves = ChessEngine.getLegalMoves(state, state.turn)
                    assertTrue(
                        "Lesson ${lesson.id} step $stepIdx has no legal moves",
                        legalMoves.isNotEmpty()
                    )

                    when (val goal = step.goal) {
                        is PracticeGoal.SpecificMove -> {
                            val candidate = legalMoves.find {
                                it.from == goal.from && it.to == goal.to &&
                                    (goal.promotionPiece == null || it.promotionPiece == goal.promotionPiece)
                            }
                            assertNotNull(
                                "Lesson ${lesson.id} step $stepIdx: SpecificMove ${goal.from} -> ${goal.to} is NOT in legal moves: $legalMoves",
                                candidate
                            )
                            val nextState = ChessEngine.applyMove(state, candidate!!)
                            assertNotNull(nextState)
                        }
                        is PracticeGoal.MovePieceToTarget -> {
                            val pieceMoves = legalMoves.filter { it.from == goal.from }
                            val reachingTarget = pieceMoves.filter { it.to in goal.targetSquares }
                            assertTrue(
                                "Lesson ${lesson.id} step $stepIdx: no move from ${goal.from} reaches targets ${goal.targetSquares}. Available: $pieceMoves",
                                reachingTarget.isNotEmpty()
                            )
                        }
                        is PracticeGoal.CaptureAllTargets -> {
                            val pieceMoves = legalMoves.filter { it.to in goal.targetSquares }
                            assertTrue(
                                "Lesson ${lesson.id} step $stepIdx: CaptureAllTargets has no legal moves into targets ${goal.targetSquares}",
                                pieceMoves.isNotEmpty()
                            )
                        }
                        is PracticeGoal.GiveCheckmate -> {
                            val mateMoves = legalMoves.filter { move ->
                                val nextState = ChessEngine.applyMove(state, move)
                                nextState.status == GameStatus.CHECKMATE
                            }
                            assertTrue(
                                "Lesson ${lesson.id} step $stepIdx: no legal move delivers checkmate!",
                                mateMoves.isNotEmpty()
                            )
                        }
                        is PracticeGoal.EscapeCheck -> {
                            assertTrue(
                                "Lesson ${lesson.id} step $stepIdx: state should be in check",
                                state.status == GameStatus.CHECK
                            )
                            assertTrue(
                                "Lesson ${lesson.id} step $stepIdx: should have moves to escape check",
                                legalMoves.isNotEmpty()
                            )
                            // Every legal move generated by ChessEngine in CHECK escapes check
                            for (move in legalMoves) {
                                val nextState = ChessEngine.applyMove(state, move)
                                val kingAttacked = ChessEngine.isKingInCheck(nextState.board, state.turn)
                                assertFalse("Escape move leaves king in check", kingAttacked)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testQuizStepsAreValid() {
        for (lesson in LessonRepository.lessons) {
            for ((stepIdx, step) in lesson.steps.withIndex()) {
                if (step is QuizStep) {
                    val state = ChessEngine.stateFromFen(step.fen)
                    if (lesson.id == "board_intro") {
                        // Board intro tests center squares identification
                        assertEquals(4, step.correctSquares.size)
                    } else {
                        val legalMovesForPiece = ChessEngine.getLegalMovesForPiece(state, step.piecePos)
                        val legalDests = legalMovesForPiece.map { it.to }.toSet()
                        assertEquals(
                            "Lesson ${lesson.id} step $stepIdx: quiz correctSquares must match legal destinations",
                            legalDests,
                            step.correctSquares
                        )
                    }
                }
            }
        }
    }
}
