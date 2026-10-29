package com.harrylong.tvchess.engine

import com.harrylong.tvchess.model.*
import kotlin.math.abs
import kotlin.random.Random

object ChessAI {

    private const val CHECKMATE_SCORE = 30000
    private const val TT_FLAG_EXACT = 0
    private const val TT_FLAG_LOWERBOUND = 1
    private const val TT_FLAG_UPPERBOUND = 2
    private const val TT_SIZE = 1 shl 16 // 65,536 mục

    internal var lastSearchedDepth: Int = 0

    private val PAWN_PST = arrayOf(
        intArrayOf(0,  0,  0,  0,  0,  0,  0,  0),
        intArrayOf(50, 50, 50, 50, 50, 50, 50, 50),
        intArrayOf(10, 10, 20, 30, 30, 20, 10, 10),
        intArrayOf(5,  5, 10, 25, 25, 10,  5,  5),
        intArrayOf(0,  0,  0, 20, 20,  0,  0,  0),
        intArrayOf(5, -5,-10,  0,  0,-10, -5,  5),
        intArrayOf(5, 10, 10,-20,-20, 10, 10,  5),
        intArrayOf(0,  0,  0,  0,  0,  0,  0,  0)
    )

    private val KNIGHT_PST = arrayOf(
        intArrayOf(-50,-40,-30,-30,-30,-30,-40,-50),
        intArrayOf(-40,-20,  0,  0,  0,  0,-20,-40),
        intArrayOf(-30,  0, 10, 15, 15, 10,  0,-30),
        intArrayOf(-30,  5, 15, 20, 20, 15,  5,-30),
        intArrayOf(-30,  0, 15, 20, 20, 15,  0,-30),
        intArrayOf(-30,  5, 10, 15, 15, 10,  5,-30),
        intArrayOf(-40,-20,  0,  5,  5,  0,-20,-40),
        intArrayOf(-50,-40,-30,-30,-30,-30,-40,-50)
    )

    private val BISHOP_PST = arrayOf(
        intArrayOf(-20,-10,-10,-10,-10,-10,-10,-20),
        intArrayOf(-10,  0,  0,  0,  0,  0,  0,-10),
        intArrayOf(-10,  0,  5, 10, 10,  5,  0,-10),
        intArrayOf(-10,  5,  5, 10, 10,  5,  5,-10),
        intArrayOf(-10,  0, 10, 10, 10, 10,  0,-10),
        intArrayOf(-10, 10, 10, 10, 10, 10, 10,-10),
        intArrayOf(-10,  5,  0,  0,  0,  0,  5,-10),
        intArrayOf(-20,-10,-10,-10,-10,-10,-10,-20)
    )

    private val ROOK_PST = arrayOf(
        intArrayOf(0,  0,  0,  0,  0,  0,  0,  0),
        intArrayOf(5, 10, 10, 10, 10, 10, 10,  5),
        intArrayOf(-5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf(-5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf(-5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf(-5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf(-5,  0,  0,  0,  0,  0,  0, -5),
        intArrayOf(0,  0,  0,  5,  5,  0,  0,  0)
    )

    private val QUEEN_PST = arrayOf(
        intArrayOf(-20,-10,-10, -5, -5,-10,-10,-20),
        intArrayOf(-10,  0,  0,  0,  0,  0,  0,-10),
        intArrayOf(-10,  0,  5,  5,  5,  5,  0,-10),
        intArrayOf(-5,   0,  5,  5,  5,  5,  0, -5),
        intArrayOf(0,    0,  5,  5,  5,  5,  0, -5),
        intArrayOf(-10,  5,  5,  5,  5,  5,  0,-10),
        intArrayOf(-10,  0,  5,  0,  0,  0,  0,-10),
        intArrayOf(-20,-10,-10, -5, -5,-10,-10,-20)
    )

    private val KING_MID_PST = arrayOf(
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-20,-30,-30,-40,-40,-30,-30,-20),
        intArrayOf(-10,-20,-20,-20,-20,-20,-20,-10),
        intArrayOf(20,  20,  0,  0,  0,  0, 20, 20),
        intArrayOf(20,  30, 10,  0,  0, 10, 30, 20)
    )

    private val KING_END_PST = arrayOf(
        intArrayOf(-50,-40,-30,-20,-20,-30,-40,-50),
        intArrayOf(-30,-20,-10,  0,  0,-10,-20,-30),
        intArrayOf(-30,-10, 20, 30, 30, 20,-10,-30),
        intArrayOf(-30,-10, 30, 40, 40, 30,-10,-30),
        intArrayOf(-30,-10, 30, 40, 40, 30,-10,-30),
        intArrayOf(-30,-10, 20, 30, 30, 20,-10,-30),
        intArrayOf(-30,-30,  0,  0,  0,  0,-30,-30),
        intArrayOf(-50,-30,-30,-30,-30,-30,-30,-50)
    )

    // Bảng băm Zobrist cho Transposition Table
    private object Zobrist {
        val pieces = Array(2) { Array(6) { LongArray(64) } }
        val blackTurn: Long
        val castling = LongArray(16)
        val enPassant = LongArray(8)

        init {
            val rng = java.util.Random(0x43686573734149L)
            for (c in 0..1) {
                for (pt in 0..5) {
                    for (sq in 0..63) {
                        pieces[c][pt][sq] = rng.nextLong()
                    }
                }
            }
            blackTurn = rng.nextLong()
            for (i in 0..15) {
                castling[i] = rng.nextLong()
            }
            for (i in 0..7) {
                enPassant[i] = rng.nextLong()
            }
        }

        fun computeHash(state: GameState): Long {
            var h = 0L
            for (r in 0..7) {
                for (c in 0..7) {
                    val p = state.board[r][c] ?: continue
                    val colorIdx = if (p.color == PieceColor.WHITE) 0 else 1
                    val typeIdx = p.type.ordinal
                    val sq = (r shl 3) or c
                    h = h xor pieces[colorIdx][typeIdx][sq]
                }
            }
            if (state.turn == PieceColor.BLACK) {
                h = h xor blackTurn
            }
            var crMask = 0
            if (state.castlingRights.whiteKingside) crMask = crMask or 1
            if (state.castlingRights.whiteQueenside) crMask = crMask or 2
            if (state.castlingRights.blackKingside) crMask = crMask or 4
            if (state.castlingRights.blackQueenside) crMask = crMask or 8
            h = h xor castling[crMask]

            val ep = state.enPassantTarget
            if (ep != null && ep.col in 0..7) {
                h = h xor enPassant[ep.col]
            }
            return h
        }
    }

    private class TTEntry {
        var key: Long = 0L
        var depth: Int = -1
        var score: Int = 0
        var flag: Int = 0
        var bestMove: ChessMove? = null

        fun reset() {
            key = 0L
            depth = -1
            score = 0
            flag = 0
            bestMove = null
        }
    }

    private val transpositionTable = Array(TT_SIZE) { TTEntry() }
    private val killerMoves = Array(64) { arrayOfNulls<ChessMove>(2) }
    private val aiLock = Any()

    private fun clearTranspositionTable() {
        for (i in 0 until TT_SIZE) {
            transpositionTable[i].reset()
        }
    }

    private fun clearKillerMoves() {
        for (i in 0 until 64) {
            killerMoves[i][0] = null
            killerMoves[i][1] = null
        }
    }

    private fun recordKillerMove(ply: Int, move: ChessMove) {
        if (ply in 0..63 && move.capturedPiece == null && move.promotionPiece == null) {
            if (killerMoves[ply][0] != move) {
                killerMoves[ply][1] = killerMoves[ply][0]
                killerMoves[ply][0] = move
            }
        }
    }

    private data class AiProfile(
        val maxDepth: Int,
        val useIterativeDeepening: Boolean,
        val timeLimitMs: Long,
        val useTranspositionTable: Boolean,
        val useQuiescence: Boolean,
        val useKillerMoves: Boolean,
        val useOpeningBook: Boolean,
        val blunderChance: Float,
        val pickSuboptimalChance: Float,
        val noiseRange: IntRange
    )

    private val PROFILES = mapOf(
        Difficulty.EASY to AiProfile(
            maxDepth = 2,
            useIterativeDeepening = false,
            timeLimitMs = 1000L,
            useTranspositionTable = false,
            useQuiescence = false,
            useKillerMoves = false,
            useOpeningBook = false,
            blunderChance = 0.35f,
            pickSuboptimalChance = 0.0f,
            noiseRange = -35..35
        ),
        Difficulty.MEDIUM to AiProfile(
            maxDepth = 3,
            useIterativeDeepening = false,
            timeLimitMs = 1500L,
            useTranspositionTable = false,
            useQuiescence = true,
            useKillerMoves = false,
            useOpeningBook = false,
            blunderChance = 0.0f,
            pickSuboptimalChance = 0.10f,
            noiseRange = -4..4
        ),
        Difficulty.HARD to AiProfile(
            maxDepth = 5,
            useIterativeDeepening = true,
            timeLimitMs = 2500L,
            useTranspositionTable = true,
            useQuiescence = true,
            useKillerMoves = true,
            useOpeningBook = true,
            blunderChance = 0.0f,
            pickSuboptimalChance = 0.0f,
            noiseRange = 0..0
        )
    )

    // Sách khai cuộc nhỏ được nạp khi khởi động
    private val OPENING_BOOK = mutableMapOf<String, MutableList<Pair<Position, Position>>>()

    init {
        loadOpeningBook()
    }

    private fun getBoardKey(board: List<List<Piece?>>, turn: PieceColor): String {
        val sb = StringBuilder(66)
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c]
                if (p == null) {
                    sb.append('.')
                } else {
                    val ch = when (p.type) {
                        PieceType.PAWN -> 'p'
                        PieceType.KNIGHT -> 'n'
                        PieceType.BISHOP -> 'b'
                        PieceType.ROOK -> 'r'
                        PieceType.QUEEN -> 'q'
                        PieceType.KING -> 'k'
                    }
                    sb.append(if (p.color == PieceColor.WHITE) ch.uppercaseChar() else ch)
                }
            }
        }
        sb.append(if (turn == PieceColor.WHITE) 'w' else 'b')
        return sb.toString()
    }

    private fun registerBookLine(vararg moves: Pair<String, String>) {
        var state = ChessEngine.createInitialState()
        for ((fromStr, toStr) in moves) {
            val from = Position.fromAlgebraic(fromStr) ?: return
            val to = Position.fromAlgebraic(toStr) ?: return
            val key = getBoardKey(state.board, state.turn)
            val list = OPENING_BOOK.getOrPut(key) { mutableListOf() }
            val pair = from to to
            if (pair !in list) {
                list.add(pair)
            }
            val legalMoves = ChessEngine.getLegalMoves(state)
            val move = legalMoves.firstOrNull { it.from == from && it.to == to } ?: return
            state = ChessEngine.applyMove(state, move)
        }
    }

    private fun loadOpeningBook() {
        // Khai cuộc Ý (Italian Game) & Giuoco Piano
        registerBookLine("e2" to "e4", "e7" to "e5", "g1" to "f3", "b8" to "c6", "f1" to "c4", "f8" to "c5")
        registerBookLine("e2" to "e4", "e7" to "e5", "g1" to "f3", "b8" to "c6", "f1" to "c4", "g8" to "f6")
        // Khai cuộc Tây Ban Nha (Ruy Lopez)
        registerBookLine("e2" to "e4", "e7" to "e5", "g1" to "f3", "b8" to "c6", "f1" to "b5", "a7" to "a6")
        // Khai cuộc Scotch
        registerBookLine("e2" to "e4", "e7" to "e5", "g1" to "f3", "b8" to "c6", "d2" to "d4", "e5" to "d4")
        // Phòng thủ Nga (Petrov)
        registerBookLine("e2" to "e4", "e7" to "e5", "g1" to "f3", "g8" to "f6", "f3" to "e5")
        // Phòng thủ Sicilian
        registerBookLine("e2" to "e4", "c7" to "c5", "g1" to "f3", "d7" to "d6", "d2" to "d4", "c5" to "d4")
        registerBookLine("e2" to "e4", "c7" to "c5", "b1" to "c3", "b8" to "c6")
        // Phòng thủ Pháp (French)
        registerBookLine("e2" to "e4", "e7" to "e6", "d2" to "d4", "d7" to "d5")
        // Phòng thủ Caro-Kann
        registerBookLine("e2" to "e4", "c7" to "c6", "d2" to "d4", "d7" to "d5")
        // Khai cuộc Gambit Hậu (Queen's Gambit) & Slav
        registerBookLine("d2" to "d4", "d7" to "d5", "c2" to "c4", "e7" to "e6", "b1" to "c3", "g8" to "f6")
        registerBookLine("d2" to "d4", "d7" to "d5", "c2" to "c4", "c7" to "c6", "g1" to "f3", "g8" to "f6")
        registerBookLine("d2" to "d4", "d7" to "d5", "c2" to "c4", "d5" to "c4", "g1" to "f3")
        // Các hệ thống Phòng thủ Ấn Độ (Indian Defenses)
        registerBookLine("d2" to "d4", "g8" to "f6", "c2" to "c4", "e7" to "e6", "g1" to "f3", "d7" to "d5")
        registerBookLine("d2" to "d4", "g8" to "f6", "c2" to "c4", "g7" to "g6", "b1" to "c3", "d7" to "d5")
        // Khai cuộc Anh (English Opening) & Reti
        registerBookLine("c2" to "c4", "e7" to "e5", "b1" to "c3", "g8" to "f6")
        registerBookLine("g1" to "f3", "d7" to "d5", "d2" to "d4")
    }

    private fun getBookMoves(state: GameState): List<Pair<Position, Position>> {
        val key = getBoardKey(state.board, state.turn)
        return OPENING_BOOK[key] ?: emptyList()
    }

    private class SearchContext(
        val startTimeNs: Long,
        val timeLimitNs: Long
    ) {
        var nodesSearched: Int = 0
        var isAborted: Boolean = false

        fun shouldAbort(): Boolean {
            if (isAborted) return true
            nodesSearched++
            if ((nodesSearched and 511) == 0) {
                if (System.nanoTime() - startTimeNs >= timeLimitNs) {
                    isAborted = true
                }
            }
            return isAborted
        }
    }

    fun findBestMove(state: GameState, difficulty: Difficulty): ChessMove? {
        val profile = PROFILES[difficulty] ?: PROFILES.getValue(Difficulty.MEDIUM)
        return findBestMove(state, difficulty, profile.timeLimitMs)
    }

    internal fun findBestMove(state: GameState, difficulty: Difficulty, timeLimitMs: Long): ChessMove? = synchronized(aiLock) {
        val legalMoves = ChessEngine.getLegalMoves(state)
        if (legalMoves.isEmpty()) return null
        if (legalMoves.size == 1) return legalMoves.first()

        val profile = PROFILES[difficulty] ?: PROFILES.getValue(Difficulty.MEDIUM)

        // 1. Kiểm tra chiếu hết trong 1 nước đi ở MEDIUM và HARD
        if (difficulty != Difficulty.EASY) {
            for (move in legalMoves) {
                val nextState = ChessEngine.applyMove(state, move)
                if (nextState.status == GameStatus.CHECKMATE) {
                    lastSearchedDepth = 1
                    return move
                }
            }
        }

        // 2. Chế độ DỄ: ~35% chọn một nước đi hợp lệ ngẫu nhiên (dễ bỏ sót quân bị ăn)
        if (difficulty == Difficulty.EASY && Random.nextFloat() < profile.blunderChance) {
            lastSearchedDepth = 0
            return legalMoves.random()
        }

        // 3. Chế độ KHÓ: Sách khai cuộc
        if (profile.useOpeningBook) {
            val bookMoves = getBookMoves(state)
            if (bookMoves.isNotEmpty()) {
                val matching = legalMoves.filter { m ->
                    bookMoves.any { it.first == m.from && it.second == m.to }
                }
                if (matching.isNotEmpty()) {
                    val idx = abs(state.board.hashCode()) % matching.size
                    lastSearchedDepth = 0
                    return matching[idx]
                }
            }
        }

        clearTranspositionTable()
        clearKillerMoves()

        val startTimeNs = System.nanoTime()
        val timeLimitNs = timeLimitMs * 1_000_000L
        val context = SearchContext(startTimeNs, timeLimitNs)

        val orderedMoves = orderMoves(legalMoves, null, 0, profile)
        var bestMoveOverall: ChessMove = orderedMoves.first()
        var bestScoreOverall = -CHECKMATE_SCORE - 1000

        val maxDepthToSearch = if (difficulty == Difficulty.EASY) {
            // Độ sâu 1 hoặc 2 không quiescence để trẻ em có cơ hội thắng
            if (Random.nextBoolean()) 1 else 2
        } else {
            profile.maxDepth
        }

        if (!profile.useIterativeDeepening) {
            // Tìm kiếm độ sâu cố định (DỄ và TRUNG BÌNH)
            val scoredMoves = mutableListOf<Pair<ChessMove, Int>>()
            var currentAlpha = -CHECKMATE_SCORE
            val beta = CHECKMATE_SCORE

            for (move in orderedMoves) {
                val nextState = applyFastMove(state, move)
                val score = -negamax(
                    state = nextState,
                    depth = maxDepthToSearch - 1,
                    ply = 1,
                    alpha = -beta,
                    beta = -currentAlpha,
                    profile = profile,
                    context = context
                )

                if (score > currentAlpha) {
                    currentAlpha = score
                    bestScoreOverall = score
                    bestMoveOverall = move
                    scoredMoves.add(move to score)
                } else if (difficulty == Difficulty.EASY && Random.nextFloat() < 0.25f) {
                    // Mức DỄ: Cho phép chọn nước kém hơn đôi chút
                    bestMoveOverall = move
                }
            }

            // Ở mức TRUNG BÌNH (King Kong): ~10% chọn nước tốt thứ 2 hoặc 3 nếu điểm số gần kề
            if (profile.pickSuboptimalChance > 0f && scoredMoves.size >= 2 && Random.nextFloat() < profile.pickSuboptimalChance) {
                val topScore = scoredMoves.last().second
                if (topScore < CHECKMATE_SCORE - 500) {
                    val candidatePool = scoredMoves.dropLast(1).filter { topScore - it.second in 1..80 }
                    if (candidatePool.isNotEmpty()) {
                        lastSearchedDepth = maxDepthToSearch
                        return candidatePool.random().first
                    }
                }
            }

            lastSearchedDepth = maxDepthToSearch
            return bestMoveOverall
        } else {
            // Tìm kiếm lặp sâu dần (Iterative Deepening) cho Godzilla (KHÓ)
            for (d in 1..maxDepthToSearch) {
                val elapsed = System.nanoTime() - startTimeNs
                if (d > 1 && elapsed >= (timeLimitNs * 5) / 10) {
                    break
                }

                var currentAlpha = -CHECKMATE_SCORE
                val beta = CHECKMATE_SCORE
                var bestMoveThisDepth: ChessMove? = null
                var bestScoreThisDepth = -CHECKMATE_SCORE - 1000

                val rootMoves = orderedMoves.sortedByDescending { if (it == bestMoveOverall) 1 else 0 }
                var depthCompleted = true

                for (move in rootMoves) {
                    if (context.shouldAbort()) {
                        depthCompleted = false
                        break
                    }

                    val nextState = applyFastMove(state, move)
                    val score = -negamax(
                        state = nextState,
                        depth = d - 1,
                        ply = 1,
                        alpha = -beta,
                        beta = -currentAlpha,
                        profile = profile,
                        context = context
                    )

                    if (context.isAborted) {
                        depthCompleted = false
                        break
                    }

                    if (score > bestScoreThisDepth) {
                        bestScoreThisDepth = score
                        bestMoveThisDepth = move
                    }
                    if (score > currentAlpha) {
                        currentAlpha = score
                    }
                }

                if (depthCompleted && bestMoveThisDepth != null) {
                    bestMoveOverall = bestMoveThisDepth
                    bestScoreOverall = bestScoreThisDepth
                    lastSearchedDepth = d

                    if (bestScoreOverall >= CHECKMATE_SCORE - 100) {
                        break
                    }
                }

                if (context.isAborted) break
            }

            bestMoveOverall
        }
    }

    private fun negamax(
        state: GameState,
        depth: Int,
        ply: Int,
        alpha: Int,
        beta: Int,
        profile: AiProfile,
        context: SearchContext
    ): Int {
        if (context.shouldAbort()) return 0

        when (state.status) {
            GameStatus.CHECKMATE -> return -(CHECKMATE_SCORE - ply)
            GameStatus.STALEMATE,
            GameStatus.DRAW_FIFTY_MOVE,
            GameStatus.DRAW_INSUFFICIENT_MATERIAL,
            GameStatus.DRAW_REPETITION -> return 0
            else -> {}
        }

        val hashKey = if (profile.useTranspositionTable) Zobrist.computeHash(state) else 0L
        var ttMove: ChessMove? = null

        if (profile.useTranspositionTable) {
            val entry = transpositionTable[hashKey.toInt() and (TT_SIZE - 1)]
            if (entry.key == hashKey && entry.depth >= 0) {
                ttMove = entry.bestMove
                if (entry.depth >= depth) {
                    when (entry.flag) {
                        TT_FLAG_EXACT -> return entry.score
                        TT_FLAG_LOWERBOUND -> if (entry.score >= beta) return entry.score
                        TT_FLAG_UPPERBOUND -> if (entry.score <= alpha) return entry.score
                    }
                }
            }
        }

        if (depth <= 0) {
            return if (profile.useQuiescence) {
                quiescenceSearch(state, alpha, beta, 2, profile, context)
            } else {
                evaluateBoardInternal(state.board, state.turn, isHard = profile.useTranspositionTable)
            }
        }

        val legalMoves = ChessEngine.getLegalMoves(state)
        if (legalMoves.isEmpty()) {
            return if (ChessEngine.isKingInCheck(state.board, state.turn)) {
                -(CHECKMATE_SCORE - ply)
            } else {
                0
            }
        }

        val origAlpha = alpha
        var currentAlpha = alpha
        var maxEval = -CHECKMATE_SCORE - 1000
        var bestMoveThisNode: ChessMove? = null

        val ordered = orderMoves(legalMoves, ttMove, ply, profile)

        for (move in ordered) {
            val nextState = applyFastMove(state, move)
            val eval = -negamax(nextState, depth - 1, ply + 1, -beta, -currentAlpha, profile, context)
            if (context.isAborted) return 0

            if (eval > maxEval) {
                maxEval = eval
                bestMoveThisNode = move
            }
            if (eval > currentAlpha) {
                currentAlpha = eval
            }
            if (currentAlpha >= beta) {
                recordKillerMove(ply, move)
                break // Alpha-Beta Cutoff
            }
        }

        if (profile.useTranspositionTable && !context.isAborted) {
            val flag = when {
                maxEval <= origAlpha -> TT_FLAG_UPPERBOUND
                maxEval >= beta -> TT_FLAG_LOWERBOUND
                else -> TT_FLAG_EXACT
            }
            val idx = hashKey.toInt() and (TT_SIZE - 1)
            val entry = transpositionTable[idx]
            if (entry.depth <= depth || entry.key != hashKey) {
                entry.key = hashKey
                entry.depth = depth
                entry.score = maxEval
                entry.flag = flag
                entry.bestMove = bestMoveThisNode
            }
        }

        return maxEval
    }

    private fun quiescenceSearch(
        state: GameState,
        alpha: Int,
        beta: Int,
        qDepth: Int,
        profile: AiProfile,
        context: SearchContext
    ): Int {
        if (context.shouldAbort()) return 0

        val standPat = evaluateBoardInternal(state.board, state.turn, isHard = profile.useTranspositionTable)
        if (qDepth <= 0) return standPat
        if (standPat >= beta) return beta

        var currentAlpha = maxOf(alpha, standPat)

        val tacticalMoves = ChessEngine.getLegalMoves(state)
            .filter { it.capturedPiece != null || it.promotionPiece != null }

        if (tacticalMoves.isEmpty()) return standPat

        val ordered = orderMoves(tacticalMoves, null, 0, profile)
        for (move in ordered) {
            val nextState = applyFastMove(state, move)
            val score = -quiescenceSearch(nextState, -beta, -currentAlpha, qDepth - 1, profile, context)
            if (context.isAborted) return 0
            if (score >= beta) return beta
            if (score > currentAlpha) {
                currentAlpha = score
            }
        }
        return currentAlpha
    }

    /**
     * Nước đi nhanh cho thuật toán tìm kiếm:
     * Cập nhật chính xác bàn cờ, lượt đi, quyền nhập thành và ô bắt tốt qua đường (En Passant),
     * tránh sao chép các danh sách lịch sử nước đi không cần thiết trong cây tìm kiếm.
     */
    internal fun applyFastMove(state: GameState, move: ChessMove): GameState {
        val nextBoard = ChessEngine.applyMoveToBoard(state.board, move)
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
        if (cr.whiteQueenside && ((move.from.row == 7 && move.from.col == 0) || (move.to.row == 7 && move.to.col == 0))) {
            cr = cr.copy(whiteQueenside = false)
        }
        if (cr.whiteKingside && ((move.from.row == 7 && move.from.col == 7) || (move.to.row == 7 && move.to.col == 7))) {
            cr = cr.copy(whiteKingside = false)
        }
        if (cr.blackQueenside && ((move.from.row == 0 && move.from.col == 0) || (move.to.row == 0 && move.to.col == 0))) {
            cr = cr.copy(blackQueenside = false)
        }
        if (cr.blackKingside && ((move.from.row == 0 && move.from.col == 7) || (move.to.row == 0 && move.to.col == 7))) {
            cr = cr.copy(blackKingside = false)
        }

        // Cập nhật ô bắt tốt qua đường (En Passant)
        val nextEnPassant = if (move.piece.type == PieceType.PAWN && abs(move.to.row - move.from.row) == 2) {
            Position((move.from.row + move.to.row) / 2, move.from.col)
        } else {
            null
        }

        return state.copy(
            board = nextBoard,
            turn = nextTurn,
            castlingRights = cr,
            enPassantTarget = nextEnPassant,
            status = GameStatus.PLAYING
        )
    }

    private fun orderMoves(
        moves: List<ChessMove>,
        ttMove: ChessMove?,
        ply: Int,
        profile: AiProfile
    ): List<ChessMove> {
        if (moves.size <= 1) return moves
        val k1 = if (profile.useKillerMoves && ply in 0..63) killerMoves[ply][0] else null
        val k2 = if (profile.useKillerMoves && ply in 0..63) killerMoves[ply][1] else null

        return moves.sortedByDescending { move ->
            when {
                ttMove != null && move == ttMove -> 1_000_000
                move.capturedPiece != null -> {
                    // MVV-LVA: Ưu tiên dùng quân nhỏ ăn quân lớn
                    100_000 + 10 * move.capturedPiece.type.materialValue - (move.piece.type.materialValue / 10)
                }
                move.promotionPiece != null -> 90_000 + move.promotionPiece.materialValue
                move == k1 -> 50_000
                move == k2 -> 40_000
                move.isKingsideCastle || move.isQueensideCastle -> 10_000
                else -> 0
            }
        }
    }

    fun evaluateBoard(board: List<List<Piece?>>, perspective: PieceColor): Int {
        return evaluateBoardInternal(board, perspective, isHard = false)
    }

    private fun evaluateBoardInternal(board: List<List<Piece?>>, perspective: PieceColor, isHard: Boolean): Int {
        var whiteMaterial = 0
        var blackMaterial = 0

        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c] ?: continue
                if (p.type != PieceType.KING) {
                    if (p.color == PieceColor.WHITE) whiteMaterial += p.type.materialValue
                    else blackMaterial += p.type.materialValue
                }
            }
        }

        val isEndgame = (whiteMaterial <= 1300 && blackMaterial <= 1300)

        var totalWhite = 0
        var totalBlack = 0
        var whiteBishops = 0
        var blackBishops = 0

        val whitePawnsInCol = if (isHard) IntArray(8) else null
        val blackPawnsInCol = if (isHard) IntArray(8) else null

        if (isHard) {
            for (r in 0..7) {
                for (c in 0..7) {
                    val p = board[r][c] ?: continue
                    if (p.type == PieceType.PAWN) {
                        if (p.color == PieceColor.WHITE) whitePawnsInCol!![c]++
                        else blackPawnsInCol!![c]++
                    }
                }
            }
        }

        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c] ?: continue
                val tableRow = if (p.color == PieceColor.WHITE) r else (7 - r)
                val pstBonus = when (p.type) {
                    PieceType.PAWN -> PAWN_PST[tableRow][c]
                    PieceType.KNIGHT -> KNIGHT_PST[tableRow][c]
                    PieceType.BISHOP -> BISHOP_PST[tableRow][c]
                    PieceType.ROOK -> ROOK_PST[tableRow][c]
                    PieceType.QUEEN -> QUEEN_PST[tableRow][c]
                    PieceType.KING -> if (isEndgame) KING_END_PST[tableRow][c] else KING_MID_PST[tableRow][c]
                }

                var value = p.type.materialValue + pstBonus

                if (isHard) {
                    when (p.type) {
                        PieceType.PAWN -> {
                            var isPassed = true
                            val minCol = maxOf(0, c - 1)
                            val maxCol = minOf(7, c + 1)
                            if (p.color == PieceColor.WHITE) {
                                for (col in minCol..maxCol) {
                                    for (checkR in 0 until r) {
                                        val enemy = board[checkR][col]
                                        if (enemy != null && enemy.color == PieceColor.BLACK && enemy.type == PieceType.PAWN) {
                                            isPassed = false
                                            break
                                        }
                                    }
                                    if (!isPassed) break
                                }
                                if (isPassed) {
                                    val rankAdvancement = 7 - r
                                    value += if (isEndgame) 25 + rankAdvancement * 15 else 15 + rankAdvancement * 8
                                }
                            } else {
                                for (col in minCol..maxCol) {
                                    for (checkR in (r + 1)..7) {
                                        val enemy = board[checkR][col]
                                        if (enemy != null && enemy.color == PieceColor.WHITE && enemy.type == PieceType.PAWN) {
                                            isPassed = false
                                            break
                                        }
                                    }
                                    if (!isPassed) break
                                }
                                if (isPassed) {
                                    val rankAdvancement = r
                                    value += if (isEndgame) 25 + rankAdvancement * 15 else 15 + rankAdvancement * 8
                                }
                            }
                        }
                        PieceType.ROOK -> {
                            val friendlyPawns = if (p.color == PieceColor.WHITE) whitePawnsInCol!![c] else blackPawnsInCol!![c]
                            val enemyPawns = if (p.color == PieceColor.WHITE) blackPawnsInCol!![c] else whitePawnsInCol!![c]
                            if (friendlyPawns == 0) {
                                value += if (enemyPawns == 0) 25 else 15
                            }
                        }
                        PieceType.KING -> {
                            if (!isEndgame) {
                                val homeRow = if (p.color == PieceColor.WHITE) 7 else 0
                                val shieldRow = if (p.color == PieceColor.WHITE) 6 else 1
                                if (r == homeRow && (c in 5..7 || c in 0..2)) {
                                    for (sc in maxOf(0, c - 1)..minOf(7, c + 1)) {
                                        val shieldPiece = board[shieldRow][sc]
                                        if (shieldPiece != null && shieldPiece.color == p.color && shieldPiece.type == PieceType.PAWN) {
                                            value += 12
                                        }
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                }

                if (p.color == PieceColor.WHITE) {
                    totalWhite += value
                    if (p.type == PieceType.BISHOP) whiteBishops++
                } else {
                    totalBlack += value
                    if (p.type == PieceType.BISHOP) blackBishops++
                }
            }
        }

        // Thưởng cặp Tượng (Bishop Pair Bonus)
        if (whiteBishops >= 2) totalWhite += 35
        if (blackBishops >= 2) totalBlack += 35

        val diff = totalWhite - totalBlack
        return if (perspective == PieceColor.WHITE) diff else -diff
    }
}
