package com.harrylong.tvchess

import android.view.KeyEvent
import com.harrylong.tvchess.engine.ChessAI
import com.harrylong.tvchess.engine.ChessEngine
import com.harrylong.tvchess.input.DareuGamepadController
import com.harrylong.tvchess.input.GamepadAction
import com.harrylong.tvchess.model.*
import org.junit.Assert.*
import org.junit.Test

class ChessEngineTest {

    @Test
    fun initialPosition_has20LegalMovesAnd10MovablePieces() {
        val state = ChessEngine.createInitialState()
        val legalMoves = ChessEngine.getLegalMoves(state)
        assertEquals(20, legalMoves.size)

        val movablePieces = ChessEngine.getMovablePiecePositions(state)
        // 8 quân Tốt + 2 quân Mã = 10 quân có thể đi ở nước đầu tiên
        assertEquals(10, movablePieces.size)
    }

    @Test
    fun smartJump_navigatesCorrectlyWithXiaomiRemoteDPad() {
        val state = ChessEngine.createInitialState()
        val movable = ChessEngine.getMovablePiecePositions(state)

        // Đứng ở Tốt e2 (row=6, col=4), bấm sang Phải (RIGHT) phải nhảy sang Tốt f2 (row=6, col=5)
        val e2 = Position(6, 4)
        val nextRight = ChessEngine.findNextSmartJump(e2, movable, DPadDirection.RIGHT, isBoardFlipped = false)
        assertEquals(Position(6, 5), nextRight)

        // Đứng ở Tốt e2 (row=6, col=4), bấm sang Trái (LEFT) phải nhảy sang Tốt d2 (row=6, col=3)
        val nextLeft = ChessEngine.findNextSmartJump(e2, movable, DPadDirection.LEFT, isBoardFlipped = false)
        assertEquals(Position(6, 3), nextLeft)

        // Đứng ở Tốt g2 (row=6, col=6), bấm Xuống (DOWN) phải nhảy xuống Mã g1 (row=7, col=6)
        val g2 = Position(6, 6)
        val nextDown = ChessEngine.findNextSmartJump(g2, movable, DPadDirection.DOWN, isBoardFlipped = false)
        assertEquals(Position(7, 6), nextDown)
    }

    @Test
    fun foolsMate_detectsCheckmateCorrectly() {
        var state = ChessEngine.createInitialState()

        // 1. f3
        val f2f3 = ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("f2") && it.to == Position.fromAlgebraic("f3")
        }
        state = ChessEngine.applyMove(state, f2f3)

        // 1... e5
        val e7e5 = ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("e7") && it.to == Position.fromAlgebraic("e5")
        }
        state = ChessEngine.applyMove(state, e7e5)

        // 2. g4
        val g2g4 = ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("g2") && it.to == Position.fromAlgebraic("g4")
        }
        state = ChessEngine.applyMove(state, g2g4)

        // 2... Qh4#
        val d8h4 = ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("d8") && it.to == Position.fromAlgebraic("h4")
        }
        state = ChessEngine.applyMove(state, d8h4)

        assertEquals(GameStatus.CHECKMATE, state.status)
        assertTrue(state.lastMove?.notation?.endsWith("#") == true)
    }

    @Test
    fun chessAI_returnsValidMoveInAllDifficulties() {
        val state = ChessEngine.createInitialState()
        for (diff in Difficulty.entries) {
            val move = ChessAI.findBestMove(state, diff)
            assertNotNull("AI must return a legal move for $diff", move)
            assertTrue(move in ChessEngine.getLegalMoves(state))
        }
    }

    // =========================================================================
    // UNIT TEST: ĐIỀU HƯỚNG TAY CẦM DAREU H101X & REMOTE ANDROID TV (LƯỢT 3)
    // =========================================================================

    @Test
    fun boardNavigationMode_togglesCorrectly() {
        var mode = BoardNavigationMode.SMART_JUMP
        assertEquals("Nhảy thông minh", mode.vietnameseTitle)
        assertEquals("⚡", mode.badgeEmoji)

        mode = mode.next()
        assertEquals(BoardNavigationMode.FREE_GRID, mode)
        assertEquals("Đi từng ô", mode.vietnameseTitle)
        assertEquals("🎯", mode.badgeEmoji)

        mode = mode.next()
        assertEquals(BoardNavigationMode.SMART_JUMP, mode)
    }

    @Test
    fun position_stepOnGrid_unflippedBoard() {
        val e2 = Position(6, 4) // Hàng 6, cột 4
        assertFalse(false)

        // Bàn cờ chuẩn (Trắng phía dưới): UP là giảm row (lên trên), DOWN là tăng row (xuống dưới)
        assertEquals(Position(5, 4), e2.stepOnGrid(DPadDirection.UP, isBoardFlipped = false))
        assertEquals(Position(7, 4), e2.stepOnGrid(DPadDirection.DOWN, isBoardFlipped = false))
        assertEquals(Position(6, 3), e2.stepOnGrid(DPadDirection.LEFT, isBoardFlipped = false))
        assertEquals(Position(6, 5), e2.stepOnGrid(DPadDirection.RIGHT, isBoardFlipped = false))

        // Giới hạn biên 0..7
        val topLeft = Position(0, 0)
        assertEquals(Position(0, 0), topLeft.stepOnGrid(DPadDirection.UP, isBoardFlipped = false))
        assertEquals(Position(0, 0), topLeft.stepOnGrid(DPadDirection.LEFT, isBoardFlipped = false))

        val bottomRight = Position(7, 7)
        assertEquals(Position(7, 7), bottomRight.stepOnGrid(DPadDirection.DOWN, isBoardFlipped = false))
        assertEquals(Position(7, 7), bottomRight.stepOnGrid(DPadDirection.RIGHT, isBoardFlipped = false))
    }

    @Test
    fun position_stepOnGrid_flippedBoard() {
        val e2 = Position(6, 4)

        // Khi bàn cờ lật (Đen phía dưới): nhìn màn hình UP là tăng row bàn cờ thật, LEFT là tăng col
        assertEquals(Position(7, 4), e2.stepOnGrid(DPadDirection.UP, isBoardFlipped = true))
        assertEquals(Position(5, 4), e2.stepOnGrid(DPadDirection.DOWN, isBoardFlipped = true))
        assertEquals(Position(6, 5), e2.stepOnGrid(DPadDirection.LEFT, isBoardFlipped = true))
        assertEquals(Position(6, 3), e2.stepOnGrid(DPadDirection.RIGHT, isBoardFlipped = true))
    }

    @Test
    fun dareuGamepad_mapsXInputButtonsCorrectly() {
        val controller = DareuGamepadController()

        // Phím XInput chuẩn
        assertEquals(GamepadAction.PrimaryConfirmA, controller.mapKeyCodeToAction(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(GamepadAction.SecondaryCancelB, controller.mapKeyCodeToAction(KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(GamepadAction.QuickHintX, controller.mapKeyCodeToAction(KeyEvent.KEYCODE_BUTTON_X))
        assertEquals(GamepadAction.QuickUndoY, controller.mapKeyCodeToAction(KeyEvent.KEYCODE_BUTTON_Y))
        assertEquals(GamepadAction.ToggleNavModeLB, controller.mapKeyCodeToAction(KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(GamepadAction.CyclePieceRB, controller.mapKeyCodeToAction(KeyEvent.KEYCODE_BUTTON_R1))
        assertEquals(GamepadAction.SpeakPieceInfoTrigger, controller.mapKeyCodeToAction(KeyEvent.KEYCODE_BUTTON_L2))
        assertEquals(GamepadAction.SpeakPieceInfoTrigger, controller.mapKeyCodeToAction(KeyEvent.KEYCODE_BUTTON_R2))
        assertEquals(GamepadAction.ToggleMenuStart, controller.mapKeyCodeToAction(KeyEvent.KEYCODE_BUTTON_START))
        assertEquals(GamepadAction.SwitchMonsterSelect, controller.mapKeyCodeToAction(KeyEvent.KEYCODE_BUTTON_SELECT))

        // Các phím chức năng khi nhấn giữ (repeatCount > 0) phải bị chặn để tránh nhảy loạn
        assertNull(controller.mapKeyCodeToAction(KeyEvent.KEYCODE_BUTTON_A, repeatCount = 1))
        assertNull(controller.mapKeyCodeToAction(KeyEvent.KEYCODE_BUTTON_X, repeatCount = 1))
        assertNull(controller.mapKeyCodeToAction(KeyEvent.KEYCODE_BUTTON_START, repeatCount = 1))
    }

    @Test
    fun dareuGamepad_mapsDirectInputButtonsCorrectly() {
        val controller = DareuGamepadController()

        // Chế độ DirectInput của tay cầm Dareu H101X
        assertEquals(GamepadAction.QuickHintX, controller.mapKeyCodeToAction(KeyEvent.KEYCODE_BUTTON_1))
        assertEquals(GamepadAction.PrimaryConfirmA, controller.mapKeyCodeToAction(KeyEvent.KEYCODE_BUTTON_2))
        assertEquals(GamepadAction.SecondaryCancelB, controller.mapKeyCodeToAction(KeyEvent.KEYCODE_BUTTON_3))
        assertEquals(GamepadAction.QuickUndoY, controller.mapKeyCodeToAction(KeyEvent.KEYCODE_BUTTON_4))
        assertEquals(GamepadAction.ToggleNavModeLB, controller.mapKeyCodeToAction(KeyEvent.KEYCODE_BUTTON_5))
        assertEquals(GamepadAction.CyclePieceRB, controller.mapKeyCodeToAction(KeyEvent.KEYCODE_BUTTON_6))
        assertEquals(GamepadAction.SpeakPieceInfoTrigger, controller.mapKeyCodeToAction(KeyEvent.KEYCODE_BUTTON_7))
        assertEquals(GamepadAction.SpeakPieceInfoTrigger, controller.mapKeyCodeToAction(KeyEvent.KEYCODE_BUTTON_8))
        assertEquals(GamepadAction.SwitchMonsterSelect, controller.mapKeyCodeToAction(KeyEvent.KEYCODE_BUTTON_9))
        assertEquals(GamepadAction.ToggleMenuStart, controller.mapKeyCodeToAction(KeyEvent.KEYCODE_BUTTON_10))
    }

    @Test
    fun dareuGamepad_dpadRepeatCooldownWorks() {
        val controller = DareuGamepadController(dpadRepeatCooldownMs = 150L)

        // Lần nhấn đầu tiên (repeatCount = 0)
        val actionFirst = controller.mapKeyCodeToAction(KeyEvent.KEYCODE_DPAD_UP, repeatCount = 0, nowMs = 1000L)
        assertEquals(GamepadAction.Navigate(DPadDirection.UP), actionFirst)

        // Nhấn giữ nhưng chưa đủ 150ms -> Bỏ qua
        val actionTooSoon = controller.mapKeyCodeToAction(KeyEvent.KEYCODE_DPAD_UP, repeatCount = 1, nowMs = 1100L)
        assertNull(actionTooSoon)

        // Nhấn giữ sau 150ms -> Cho phép bước tiếp
        val actionValid = controller.mapKeyCodeToAction(KeyEvent.KEYCODE_DPAD_UP, repeatCount = 2, nowMs = 1160L)
        assertEquals(GamepadAction.Navigate(DPadDirection.UP), actionValid)
    }

    @Test
    fun dareuGamepad_hatDpadEvaluation() {
        val controller = DareuGamepadController(hatThreshold = 0.5f, hatRepeatIntervalMs = 165L)

        // 1. Nhấn Hat UP
        val hatUp1 = controller.evaluateStickAndHat(hatX = 0f, hatY = -1.0f, nowMs = 1000L)
        assertEquals(GamepadAction.Navigate(DPadDirection.UP, forceFreeGrid = false), hatUp1)

        // 2. Giữ Hat UP trước chu kỳ 165ms -> null
        val hatUpTooFast = controller.evaluateStickAndHat(hatX = 0f, hatY = -1.0f, nowMs = 1080L)
        assertNull(hatUpTooFast)

        // 3. Giữ Hat UP sau 165ms -> Lặp lại
        val hatUpRepeat = controller.evaluateStickAndHat(hatX = 0f, hatY = -1.0f, nowMs = 1170L)
        assertEquals(GamepadAction.Navigate(DPadDirection.UP, forceFreeGrid = false), hatUpRepeat)

        // 4. Nhả Hat về trung tâm
        val hatNeutral = controller.evaluateStickAndHat(hatX = 0f, hatY = 0f, nowMs = 1200L)
        assertNull(hatNeutral)

        // 5. Gạt sang Hat RIGHT -> Nhận ngay lập tức
        val hatRight = controller.evaluateStickAndHat(hatX = 1.0f, hatY = 0f, nowMs = 1210L)
        assertEquals(GamepadAction.Navigate(DPadDirection.RIGHT, forceFreeGrid = false), hatRight)
    }

    @Test
    fun dareuGamepad_leftAnalogStickDeadzoneAndCooldown() {
        val controller = DareuGamepadController(
            leftStickDeadzone = 0.38f,
            leftStickCooldownMs = 180L
        )

        // Rung nhẹ trong vùng chống trôi Deadzone (< 0.38f) -> Phải bị triệt tiêu
        val drift = controller.evaluateStickAndHat(leftX = 0.20f, leftY = -0.15f, nowMs = 1000L)
        assertNull(drift)

        // Đẩy cần sang TRÁI vượt Deadzone
        val moveLeft = controller.evaluateStickAndHat(leftX = -0.75f, leftY = 0.1f, nowMs = 1010L)
        assertEquals(GamepadAction.Navigate(DPadDirection.LEFT, forceFreeGrid = false), moveLeft)

        // Giữ cần TRÁI trong lúc chưa hết Cooldown 180ms
        val holdLeft = controller.evaluateStickAndHat(leftX = -0.75f, leftY = 0.1f, nowMs = 1100L)
        assertNull(holdLeft)

        // Giữ cần TRÁI sau 180ms -> Bước tiếp
        val repeatLeft = controller.evaluateStickAndHat(leftX = -0.75f, leftY = 0.1f, nowMs = 1200L)
        assertEquals(GamepadAction.Navigate(DPadDirection.LEFT, forceFreeGrid = false), repeatLeft)

        // Nhả về trung tâm (Neutral)
        controller.evaluateStickAndHat(leftX = 0f, leftY = 0f, nowMs = 1250L)

        // Đẩy ngay sang PHẢI -> Phát lệnh lập tức không cần đợi cooldown
        val moveRight = controller.evaluateStickAndHat(leftX = 0.8f, leftY = 0f, nowMs = 1260L)
        assertEquals(GamepadAction.Navigate(DPadDirection.RIGHT, forceFreeGrid = false), moveRight)
    }

    @Test
    fun dareuGamepad_rightAnalogStickForcesFreeGrid() {
        val controller = DareuGamepadController(rightStickDeadzone = 0.42f)

        // Cần Analog Phải gạt XUỐNG: Luôn phát forceFreeGrid = true để đi tự do từng ô
        val rightDown = controller.evaluateStickAndHat(rightX = 0f, rightY = 0.85f, nowMs = 1000L)
        assertNotNull(rightDown)
        assertTrue(rightDown is GamepadAction.Navigate)
        val nav = rightDown as GamepadAction.Navigate
        assertEquals(DPadDirection.DOWN, nav.direction)
        assertTrue("Cần Analog Phải luôn bật cờ forceFreeGrid", nav.forceFreeGrid)
    }

    @Test
    fun dareuGamepad_analogTriggersEdgeTriggered() {
        val controller = DareuGamepadController(triggerThreshold = 0.6f)

        // Chạm nhẹ cò (< 0.6f)
        val lightPull = controller.evaluateStickAndHat(lTrigger = 0.35f, nowMs = 1000L)
        assertNull(lightPull)

        // Bóp sâu cò L2 (> 0.6f) -> Phát lệnh đọc giọng hướng dẫn quân cờ
        val triggerPulled = controller.evaluateStickAndHat(lTrigger = 0.85f, nowMs = 1010L)
        assertEquals(GamepadAction.SpeakPieceInfoTrigger, triggerPulled)

        // Vẫn giữ sâu cò -> Edge-triggered phải trả về null, tránh spam giọng đọc liên tục
        val triggerHeld = controller.evaluateStickAndHat(lTrigger = 0.90f, nowMs = 1100L)
        assertNull(triggerHeld)

        // Nhả cò
        controller.evaluateStickAndHat(lTrigger = 0f, nowMs = 1200L)

        // Bóp lại cò R2 -> Phát lại lệnh
        val triggerR2 = controller.evaluateStickAndHat(rTrigger = 0.80f, nowMs = 1250L)
        assertEquals(GamepadAction.SpeakPieceInfoTrigger, triggerR2)
    }

    // =========================================================================
    // KIỂM THỬ TOÀN DIỆN MỘT TRẬN CỜ HOÀN CHỈNH & KHÔNG BỊ MẤT NƯỚC ĐI
    // =========================================================================

    @Test
    fun sequentialMoves_movingKnightRetainsPreviousPawnMove() {
        var state = ChessEngine.createInitialState()

        // Nước 1 Trắng: Đi tốt e2 -> e4
        val e2 = Position.fromAlgebraic("e2")!!
        val e4 = Position.fromAlgebraic("e4")!!
        val e2e4 = ChessEngine.getLegalMovesForPiece(state, e2).first { it.to == e4 }
        state = ChessEngine.applyMove(state, e2e4)

        // Kiểm tra sau nước 1: Tốt trắng đã ở e4, ô e2 rỗng
        assertNull(state.pieceAt(e2))
        assertEquals(Piece(PieceType.PAWN, PieceColor.WHITE), state.pieceAt(e4))
        assertEquals(PieceColor.BLACK, state.turn)
        assertEquals(1, state.moveHistory.size)

        // Nước 1 Đen: Đi tốt e7 -> e5
        val e7 = Position.fromAlgebraic("e7")!!
        val e5 = Position.fromAlgebraic("e5")!!
        val e7e5 = ChessEngine.getLegalMovesForPiece(state, e7).first { it.to == e5 }
        state = ChessEngine.applyMove(state, e7e5)

        // Kiểm tra sau nước 1 của Đen: Tốt e4 của Trắng VẪN NGUYÊN VẸN ở e4
        assertEquals(Piece(PieceType.PAWN, PieceColor.WHITE), state.pieceAt(e4))
        assertEquals(Piece(PieceType.PAWN, PieceColor.BLACK), state.pieceAt(e5))
        assertEquals(PieceColor.WHITE, state.turn)
        assertEquals(2, state.moveHistory.size)

        // Nước 2 Trắng: Đi Mã g1 -> f3
        val g1 = Position.fromAlgebraic("g1")!!
        val f3 = Position.fromAlgebraic("f3")!!
        val g1f3 = ChessEngine.getLegalMovesForPiece(state, g1).first { it.to == f3 }
        state = ChessEngine.applyMove(state, g1f3)

        // KIỂM TRA QUAN TRỌNG NHẤT:
        // Di chuyển quân Mã g1 -> f3 KHÔNG ĐƯỢC làm mất nước đi tốt e4 trước đó!
        assertEquals(Piece(PieceType.KNIGHT, PieceColor.WHITE), state.pieceAt(f3))
        assertNull(state.pieceAt(g1))
        assertEquals("Tốt e4 phải còn nguyên sau khi đi mã!", Piece(PieceType.PAWN, PieceColor.WHITE), state.pieceAt(e4))
        assertNull("Ô e2 vẫn phải rỗng!", state.pieceAt(e2))
        assertEquals(Piece(PieceType.PAWN, PieceColor.BLACK), state.pieceAt(e5))
        assertEquals(PieceColor.BLACK, state.turn)
        assertEquals(3, state.moveHistory.size)
        assertEquals("e4", state.moveHistory[0].notation)
        assertEquals("e5", state.moveHistory[1].notation)
        assertEquals("Mf3", state.moveHistory[2].notation)

        // Nước 2 Đen: Đi Mã b8 -> c6
        val b8 = Position.fromAlgebraic("b8")!!
        val c6 = Position.fromAlgebraic("c6")!!
        val b8c6 = ChessEngine.getLegalMovesForPiece(state, b8).first { it.to == c6 }
        state = ChessEngine.applyMove(state, b8c6)

        // Kiểm tra toàn diện bàn cờ sau 4 nước đi
        assertEquals(Piece(PieceType.PAWN, PieceColor.WHITE), state.pieceAt(e4))
        assertEquals(Piece(PieceType.PAWN, PieceColor.BLACK), state.pieceAt(e5))
        assertEquals(Piece(PieceType.KNIGHT, PieceColor.WHITE), state.pieceAt(f3))
        assertEquals(Piece(PieceType.KNIGHT, PieceColor.BLACK), state.pieceAt(c6))
        assertEquals(4, state.moveHistory.size)
    }

    @Test
    fun completeGame_scholarsMate_fullCycle() {
        var state = ChessEngine.createInitialState()

        // 1. e4 e5
        state = ChessEngine.applyMove(state, ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("e2") && it.to == Position.fromAlgebraic("e4")
        })
        state = ChessEngine.applyMove(state, ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("e7") && it.to == Position.fromAlgebraic("e5")
        })

        // 2. Qh5 Nc6
        state = ChessEngine.applyMove(state, ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("d1") && it.to == Position.fromAlgebraic("h5")
        })
        state = ChessEngine.applyMove(state, ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("b8") && it.to == Position.fromAlgebraic("c6")
        })

        // 3. Bc4 Nf6
        state = ChessEngine.applyMove(state, ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("f1") && it.to == Position.fromAlgebraic("c4")
        })
        state = ChessEngine.applyMove(state, ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("g8") && it.to == Position.fromAlgebraic("f6")
        })

        // 4. Qxf7# (Chiếu hết Học Giả)
        val qxf7 = ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("h5") && it.to == Position.fromAlgebraic("f7")
        }
        state = ChessEngine.applyMove(state, qxf7)

        assertEquals(GameStatus.CHECKMATE, state.status)
        assertTrue(state.status.isGameOver)
        assertTrue(state.lastMove?.notation?.endsWith("#") == true)
        assertEquals(PieceColor.BLACK, state.turn) // Đến lượt Đen nhưng Đen bị chiếu hết -> Trắng thắng
        assertEquals(1, state.capturedByWhite.size)
        assertEquals(PieceType.PAWN, state.capturedByWhite[0].type)
    }

    @Test
    fun completeGame_castlingKingsideAndQueenside() {
        var state = ChessEngine.createInitialState()

        // 1. e4 e5
        state = ChessEngine.applyMove(state, ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("e2") && it.to == Position.fromAlgebraic("e4")
        })
        state = ChessEngine.applyMove(state, ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("e7") && it.to == Position.fromAlgebraic("e5")
        })

        // 2. Nf3 Nc6
        state = ChessEngine.applyMove(state, ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("g1") && it.to == Position.fromAlgebraic("f3")
        })
        state = ChessEngine.applyMove(state, ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("b8") && it.to == Position.fromAlgebraic("c6")
        })

        // 3. Bc4 Bc5
        state = ChessEngine.applyMove(state, ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("f1") && it.to == Position.fromAlgebraic("c4")
        })
        state = ChessEngine.applyMove(state, ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("f8") && it.to == Position.fromAlgebraic("c5")
        })

        // 4. O-O (Trắng nhập thành cánh vua)
        val castleWhiteKingside = ChessEngine.getLegalMoves(state).firstOrNull { it.isKingsideCastle }
        assertNotNull("Trắng phải nhập thành cánh vua được", castleWhiteKingside)
        state = ChessEngine.applyMove(state, castleWhiteKingside!!)

        assertEquals("O-O", state.lastMove?.notation)
        assertEquals(Piece(PieceType.KING, PieceColor.WHITE), state.pieceAt(Position.fromAlgebraic("g1")!!))
        assertEquals(Piece(PieceType.ROOK, PieceColor.WHITE), state.pieceAt(Position.fromAlgebraic("f1")!!))
        assertNull(state.pieceAt(Position.fromAlgebraic("e1")!!))
        assertNull(state.pieceAt(Position.fromAlgebraic("h1")!!))
        assertFalse(state.castlingRights.whiteKingside)
        assertFalse(state.castlingRights.whiteQueenside)

        // 4... Qe7 5. d3 d6 6. Bg5 Be6 7. Qd2 O-O-O (Đen nhập thành cánh hậu)
        state = ChessEngine.applyMove(state, ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("d8") && it.to == Position.fromAlgebraic("e7")
        })
        state = ChessEngine.applyMove(state, ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("d2") && it.to == Position.fromAlgebraic("d3")
        })
        state = ChessEngine.applyMove(state, ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("d7") && it.to == Position.fromAlgebraic("d6")
        })
        state = ChessEngine.applyMove(state, ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("c1") && it.to == Position.fromAlgebraic("g5")
        })
        state = ChessEngine.applyMove(state, ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("c8") && it.to == Position.fromAlgebraic("e6")
        })
        state = ChessEngine.applyMove(state, ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("d1") && it.to == Position.fromAlgebraic("d2")
        })

        // Đen nhập thành cánh hậu (O-O-O)
        val castleBlackQueenside = ChessEngine.getLegalMoves(state).firstOrNull { it.isQueensideCastle }
        assertNotNull("Đen phải nhập thành cánh hậu được", castleBlackQueenside)
        state = ChessEngine.applyMove(state, castleBlackQueenside!!)

        assertEquals("O-O-O", state.lastMove?.notation)
        assertEquals(Piece(PieceType.KING, PieceColor.BLACK), state.pieceAt(Position.fromAlgebraic("c8")!!))
        assertEquals(Piece(PieceType.ROOK, PieceColor.BLACK), state.pieceAt(Position.fromAlgebraic("d8")!!))
        assertNull(state.pieceAt(Position.fromAlgebraic("e8")!!))
        assertNull(state.pieceAt(Position.fromAlgebraic("a8")!!))
        assertFalse(state.castlingRights.blackQueenside)
        assertFalse(state.castlingRights.blackKingside)
    }

    @Test
    fun enPassant_executionAndTargetExpiration() {
        var state = ChessEngine.createInitialState()

        // 1. e4 a6 2. e5 d5
        state = ChessEngine.applyMove(state, ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("e2") && it.to == Position.fromAlgebraic("e4")
        })
        state = ChessEngine.applyMove(state, ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("a7") && it.to == Position.fromAlgebraic("a6")
        })
        state = ChessEngine.applyMove(state, ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("e4") && it.to == Position.fromAlgebraic("e5")
        })
        state = ChessEngine.applyMove(state, ChessEngine.getLegalMoves(state).first {
            it.from == Position.fromAlgebraic("d7") && it.to == Position.fromAlgebraic("d5")
        })

        // Ô En Passant target phải là d6
        assertEquals(Position.fromAlgebraic("d6"), state.enPassantTarget)

        // Nước đi ăn Tốt qua đường exd6 phải có mặt trong legal moves
        val epMove = ChessEngine.getLegalMoves(state).firstOrNull { it.isEnPassant }
        assertNotNull("Phải có nước đi En Passant exd6", epMove)
        assertEquals(Position.fromAlgebraic("e5"), epMove!!.from)
        assertEquals(Position.fromAlgebraic("d6"), epMove.to)

        // Thực hiện En Passant
        state = ChessEngine.applyMove(state, epMove)
        assertEquals("exd6", state.lastMove?.notation)
        assertEquals(Piece(PieceType.PAWN, PieceColor.WHITE), state.pieceAt(Position.fromAlgebraic("d6")!!))
        assertNull("Quân tốt d5 của Đen phải bị loại khỏi bàn cờ", state.pieceAt(Position.fromAlgebraic("d5")!!))
        assertNull(state.pieceAt(Position.fromAlgebraic("e5")!!))
        assertEquals(1, state.capturedByWhite.size)
        assertEquals(PieceType.PAWN, state.capturedByWhite[0].type)
        assertNull("Ô En Passant target phải hết hiệu lực sau nước đi kế tiếp", state.enPassantTarget)
    }

    @Test
    fun pawnPromotion_toAllFourPieceTypesAndDiagonalCapture() {
        // Tạo bàn cờ giả lập Tốt trắng chuẩn bị phong cấp ở e7
        val board = MutableList(8) { MutableList<Piece?>(8) { null } }
        board[7][4] = Piece(PieceType.KING, PieceColor.WHITE) // e1
        board[0][7] = Piece(PieceType.KING, PieceColor.BLACK) // h8
        board[1][4] = Piece(PieceType.PAWN, PieceColor.WHITE) // Tốt trắng ở e7 (ô e8 phía trước rỗng)
        board[0][3] = Piece(PieceType.ROOK, PieceColor.BLACK) // Xe đen ở d8

        val state = GameState(
            board = board.map { it.toList() },
            turn = PieceColor.WHITE
        )

        val legal = ChessEngine.getLegalMovesForPiece(state, Position(1, 4))
        // 4 lựa chọn đi thẳng lên e8 + 4 lựa chọn ăn chéo xe ở d8 = 8 nước đi phong cấp
        assertEquals(8, legal.size)

        for (promoType in PieceType.PROMOTION_OPTIONS) {
            val pushPromo = legal.firstOrNull { it.to == Position(0, 4) && it.promotionPiece == promoType }
            assertNotNull("Phải có lựa chọn phong cấp thành $promoType", pushPromo)
            val nextState = ChessEngine.applyMove(state, pushPromo!!)
            assertEquals(Piece(promoType, PieceColor.WHITE), nextState.pieceAt(Position(0, 4)))
            assertNull(nextState.pieceAt(Position(1, 4)))
        }

        // Thử ăn chéo phong cấp Hậu: exd8=H+
        val capPromoQueen = legal.first { it.to == Position(0, 3) && it.promotionPiece == PieceType.QUEEN }
        val queenState = ChessEngine.applyMove(state, capPromoQueen)
        assertEquals(Piece(PieceType.QUEEN, PieceColor.WHITE), queenState.pieceAt(Position(0, 3)))
        assertEquals(1, queenState.capturedByWhite.size)
        assertEquals(PieceType.ROOK, queenState.capturedByWhite[0].type)
        assertTrue(queenState.lastMove?.notation?.contains("exd8=H") == true)
    }

    @Test
    fun drawConditions_stalemateAndInsufficientMaterial() {
        // 1. Stalemate (Hòa Pat): Vua đen ở a8, Vua trắng ở a6, Hậu trắng ở c7
        val staleBoard = MutableList(8) { MutableList<Piece?>(8) { null } }
        staleBoard[0][0] = Piece(PieceType.KING, PieceColor.BLACK) // a8
        staleBoard[2][0] = Piece(PieceType.KING, PieceColor.WHITE) // a6
        staleBoard[1][2] = Piece(PieceType.QUEEN, PieceColor.WHITE) // c7

        val staleState = GameState(
            board = staleBoard.map { it.toList() },
            turn = PieceColor.WHITE
        )
        // Di chuyển Hậu từ c7 -> b6 để tạo thế Hòa Pat cho Đen
        val c7b6 = ChessMove(
            from = Position(1, 2),
            to = Position(2, 1),
            piece = Piece(PieceType.QUEEN, PieceColor.WHITE)
        )
        val endState = ChessEngine.applyMove(staleState, c7b6)
        assertEquals(GameStatus.STALEMATE, endState.status)
        assertTrue(endState.status.isGameOver)

        // 2. Insufficient Material (Thiếu quân chiếu bí): Vua vs Vua
        val kVkBoard = MutableList(8) { MutableList<Piece?>(8) { null } }
        kVkBoard[7][4] = Piece(PieceType.KING, PieceColor.WHITE)
        kVkBoard[0][4] = Piece(PieceType.KING, PieceColor.BLACK)
        val kVkState = GameState(board = kVkBoard.map { it.toList() }, turn = PieceColor.WHITE)
        val moveKing = ChessMove(from = Position(7, 4), to = Position(7, 5), piece = Piece(PieceType.KING, PieceColor.WHITE))
        val kVkNext = ChessEngine.applyMove(kVkState, moveKing)
        assertEquals(GameStatus.DRAW_INSUFFICIENT_MATERIAL, kVkNext.status)
    }

    @Test
    fun drawConditions_threefoldRepetition() {
        var state = ChessEngine.createInitialState()

        // 1. Nf3 Nf6 2. Ng1 Ng8 3. Nf3 Nf6 4. Ng1 Ng8 (Lặp lại 3 lần)
        val moves = listOf(
            Position.fromAlgebraic("g1")!! to Position.fromAlgebraic("f3")!!,
            Position.fromAlgebraic("g8")!! to Position.fromAlgebraic("f6")!!,
            Position.fromAlgebraic("f3")!! to Position.fromAlgebraic("g1")!!,
            Position.fromAlgebraic("f6")!! to Position.fromAlgebraic("g8")!!,
            Position.fromAlgebraic("g1")!! to Position.fromAlgebraic("f3")!!,
            Position.fromAlgebraic("g8")!! to Position.fromAlgebraic("f6")!!,
            Position.fromAlgebraic("f3")!! to Position.fromAlgebraic("g1")!!,
            Position.fromAlgebraic("f6")!! to Position.fromAlgebraic("g8")!!
        )

        for ((from, to) in moves) {
            val move = ChessEngine.getLegalMoves(state).first { it.from == from && it.to == to }
            state = ChessEngine.applyMove(state, move)
        }

        assertEquals(GameStatus.DRAW_REPETITION, state.status)
        assertTrue(state.status.isGameOver)
    }

    @Test
    fun undoTurn_preservesPawnMoveAndBoardState() {
        var state = ChessEngine.createInitialState()
        val stack = mutableListOf<GameState>()
        stack.add(state)

        // Nước 1: Trắng e2->e4, Đen e7->e5
        val m1 = ChessEngine.getLegalMoves(state).first { it.from == Position.fromAlgebraic("e2") && it.to == Position.fromAlgebraic("e4") }
        state = ChessEngine.applyMove(state, m1)
        stack.add(state)

        val m2 = ChessEngine.getLegalMoves(state).first { it.from == Position.fromAlgebraic("e7") && it.to == Position.fromAlgebraic("e5") }
        state = ChessEngine.applyMove(state, m2)
        stack.add(state)

        // Nước 2: Trắng Nf3, Đen Nc6
        val m3 = ChessEngine.getLegalMoves(state).first { it.from == Position.fromAlgebraic("g1") && it.to == Position.fromAlgebraic("f3") }
        state = ChessEngine.applyMove(state, m3)
        stack.add(state)

        val m4 = ChessEngine.getLegalMoves(state).first { it.from == Position.fromAlgebraic("b8") && it.to == Position.fromAlgebraic("c6") }
        state = ChessEngine.applyMove(state, m4)
        stack.add(state)

        assertEquals(5, stack.size)

        // Thực hiện Undo: drop 2 state gần nhất (nước của Đen và nước của Trắng)
        val undoneStack = stack.dropLast(2)
        assertEquals(3, undoneStack.size)

        val restoredState = undoneStack.last()
        assertEquals(PieceColor.WHITE, restoredState.turn)
        assertEquals("Tốt trắng ở e4 phải được giữ nguyên vẹn sau khi Undo!", Piece(PieceType.PAWN, PieceColor.WHITE), restoredState.pieceAt(Position.fromAlgebraic("e4")!!))
        assertNull(restoredState.pieceAt(Position.fromAlgebraic("e2")!!))
        assertEquals(Piece(PieceType.PAWN, PieceColor.BLACK), restoredState.pieceAt(Position.fromAlgebraic("e5")!!))
        // Mã g1 chưa đi trong restoredState
        assertEquals(Piece(PieceType.KNIGHT, PieceColor.WHITE), restoredState.pieceAt(Position.fromAlgebraic("g1")!!))
    }

    // =========================================================================
    // UNIT TEST: KIỂM TRA NGHIÊM NGẶT LUẬT FIDE CHỐNG ĐI XUYÊN & GHIM QUÂN (PIN)
    // =========================================================================

    @Test
    fun rook_cannotPassThroughFriendlyOrEnemyPieces() {
        val board = MutableList(8) { MutableList<Piece?>(8) { null } }
        board[7][4] = Piece(PieceType.KING, PieceColor.WHITE) // e1
        board[0][4] = Piece(PieceType.KING, PieceColor.BLACK) // e8

        // Đặt Xe trắng ở d4 (row=4, col=3)
        val d4 = Position(4, 3)
        board[d4.row][d4.col] = Piece(PieceType.ROOK, PieceColor.WHITE)

        // Quân đồng minh (Tốt trắng) cản trên đường dọc tại d6 (row=2, col=3)
        val d6 = Position(2, 3)
        board[d6.row][d6.col] = Piece(PieceType.PAWN, PieceColor.WHITE)

        // Quân địch (Tốt đen) cản trên đường ngang tại g4 (row=4, col=6)
        val g4 = Position(4, 6)
        board[g4.row][g4.col] = Piece(PieceType.PAWN, PieceColor.BLACK)

        val state = GameState(board = board.map { it.toList() }, turn = PieceColor.WHITE)

        // 1. Kiểm tra isPathClear trên đường dọc có quân đồng minh cản ở d6
        val d5 = Position(3, 3)
        assertTrue("Đường từ d4 tới d5 phải thông suốt", ChessEngine.isPathClear(state.board, d4, d5))
        val d7 = Position(1, 3)
        val d8 = Position(0, 3)
        assertFalse("Không được xuyên qua quân đồng minh tại d6 để tới d7", ChessEngine.isPathClear(state.board, d4, d7))
        assertFalse("Không được xuyên qua quân đồng minh tại d6 để tới d8", ChessEngine.isPathClear(state.board, d4, d8))

        // 2. Kiểm tra isPathClear trên đường ngang có quân địch cản ở g4
        val f4 = Position(4, 5)
        assertTrue("Đường từ d4 tới f4 phải thông suốt", ChessEngine.isPathClear(state.board, d4, f4))
        val h4 = Position(4, 7)
        assertFalse("Không được nhảy qua quân địch tại g4 để tới h4", ChessEngine.isPathClear(state.board, d4, h4))

        // 3. getLegalMovesForPiece tuyệt đối không chứa bất kỳ ô nào nằm sau quân cản
        val rookMoves = ChessEngine.getLegalMovesForPiece(state, d4).map { it.to }
        assertTrue("Xe đi được tới d5 (ô trống trước quân cản)", d5 in rookMoves)
        assertFalse("Xe không được đi vào ô quân đồng minh d6", d6 in rookMoves)
        assertFalse("Xe tuyệt đối không được đi xuyên qua d6 tới d7", d7 in rookMoves)
        assertFalse("Xe tuyệt đối không được đi xuyên qua d6 tới d8", d8 in rookMoves)

        assertTrue("Xe ăn được quân địch g4", g4 in rookMoves)
        assertFalse("Xe tuyệt đối không được nhảy qua quân địch g4 tới h4", h4 in rookMoves)

        // 4. isStrictFideLegalMove từ chối mọi nước đi nhảy xuyên qua quân cản
        val illegalThroughFriendly = ChessMove(from = d4, to = d7, piece = Piece(PieceType.ROOK, PieceColor.WHITE))
        assertFalse(ChessEngine.isStrictFideLegalMove(state, illegalThroughFriendly))
        val illegalThroughEnemy = ChessMove(from = d4, to = h4, piece = Piece(PieceType.ROOK, PieceColor.WHITE))
        assertFalse(ChessEngine.isStrictFideLegalMove(state, illegalThroughEnemy))
        val illegalFriendlyCapture = ChessMove(from = d4, to = d6, piece = Piece(PieceType.ROOK, PieceColor.WHITE))
        assertFalse(ChessEngine.isStrictFideLegalMove(state, illegalFriendlyCapture))
    }

    @Test
    fun bishop_cannotPassThroughFriendlyOrEnemyPieces() {
        val board = MutableList(8) { MutableList<Piece?>(8) { null } }
        board[7][4] = Piece(PieceType.KING, PieceColor.WHITE) // e1
        board[0][4] = Piece(PieceType.KING, PieceColor.BLACK) // e8

        // Đặt Tượng trắng ở d4 (row=4, col=3)
        val d4 = Position(4, 3)
        board[d4.row][d4.col] = Piece(PieceType.BISHOP, PieceColor.WHITE)

        // Đường chéo lên-phải: Quân đồng minh (Mã trắng) ở f6 (row=2, col=5)
        // Ô e5 (row=3, col=4) rỗng; các ô sau f6 là g7 (row=1, col=6), h8 (row=0, col=7)
        val e5 = Position(3, 4)
        val f6 = Position(2, 5)
        val g7 = Position(1, 6)
        val h8 = Position(0, 7)
        board[f6.row][f6.col] = Piece(PieceType.KNIGHT, PieceColor.WHITE)

        // Đường chéo lên-trái: Quân địch (Xe đen) ở b6 (row=2, col=1)
        // Ô c5 (row=3, col=2) rỗng; ô sau b6 là a7 (row=1, col=0)
        val c5 = Position(3, 2)
        val b6 = Position(2, 1)
        val a7 = Position(1, 0)
        board[b6.row][b6.col] = Piece(PieceType.ROOK, PieceColor.BLACK)

        val state = GameState(board = board.map { it.toList() }, turn = PieceColor.WHITE)

        // 1. Kiểm tra isPathClear
        assertTrue("Đường từ d4 tới e5 phải thông suốt", ChessEngine.isPathClear(state.board, d4, e5))
        assertFalse("Tượng không được đi xuyên qua đồng minh f6 tới g7", ChessEngine.isPathClear(state.board, d4, g7))
        assertFalse("Tượng không được đi xuyên qua đồng minh f6 tới h8", ChessEngine.isPathClear(state.board, d4, h8))

        assertTrue("Đường từ d4 tới c5 phải thông suốt", ChessEngine.isPathClear(state.board, d4, c5))
        assertFalse("Tượng không được nhảy qua quân địch b6 tới a7", ChessEngine.isPathClear(state.board, d4, a7))

        // 2. Kiểm tra getLegalMovesForPiece
        val bishopMoves = ChessEngine.getLegalMovesForPiece(state, d4).map { it.to }
        assertTrue("Tượng đi được tới ô trống e5", e5 in bishopMoves)
        assertFalse("Tượng không được đi vào ô đồng minh f6", f6 in bishopMoves)
        assertFalse("Tượng không được xuyên qua f6 tới g7", g7 in bishopMoves)
        assertFalse("Tượng không được xuyên qua f6 tới h8", h8 in bishopMoves)

        assertTrue("Tượng đi được tới ô trống c5", c5 in bishopMoves)
        assertTrue("Tượng ăn được quân địch ở b6", b6 in bishopMoves)
        assertFalse("Tượng không được nhảy qua quân địch b6 tới a7", a7 in bishopMoves)

        // 3. isStrictFideLegalMove từ chối
        val movePassThroughFriendly = ChessMove(from = d4, to = g7, piece = Piece(PieceType.BISHOP, PieceColor.WHITE))
        assertFalse(ChessEngine.isStrictFideLegalMove(state, movePassThroughFriendly))
        val movePassThroughEnemy = ChessMove(from = d4, to = a7, piece = Piece(PieceType.BISHOP, PieceColor.WHITE))
        assertFalse(ChessEngine.isStrictFideLegalMove(state, movePassThroughEnemy))
    }

    @Test
    fun queen_cannotPassThroughFriendlyOrEnemyPieces() {
        val board = MutableList(8) { MutableList<Piece?>(8) { null } }
        board[0][6] = Piece(PieceType.KING, PieceColor.WHITE) // g8
        board[0][0] = Piece(PieceType.KING, PieceColor.BLACK) // a8

        // Đặt Hậu trắng ở d4 (row=4, col=3)
        val d4 = Position(4, 3)
        board[d4.row][d4.col] = Piece(PieceType.QUEEN, PieceColor.WHITE)

        // Thiết lập vật cản trên cả 8 hướng của Hậu:
        // 1. UP (-1, 0): đồng minh tại d6 (2, 3), sau đó là d7 (1, 3)
        val d6 = Position(2, 3); board[d6.row][d6.col] = Piece(PieceType.PAWN, PieceColor.WHITE)
        val d7 = Position(1, 3)

        // 2. DOWN (1, 0): địch tại d2 (6, 3), sau đó là d1 (7, 3)
        val d2 = Position(6, 3); board[d2.row][d2.col] = Piece(PieceType.PAWN, PieceColor.BLACK)
        val d1 = Position(7, 3)

        // 3. LEFT (0, -1): đồng minh tại b4 (4, 1), sau đó là a4 (4, 0)
        val b4 = Position(4, 1); board[b4.row][b4.col] = Piece(PieceType.KNIGHT, PieceColor.WHITE)
        val a4 = Position(4, 0)

        // 4. RIGHT (0, 1): địch tại f4 (4, 5), sau đó là g4 (4, 6)
        val f4 = Position(4, 5); board[f4.row][f4.col] = Piece(PieceType.BISHOP, PieceColor.BLACK)
        val g4 = Position(4, 6)

        // 5. UP-LEFT (-1, -1): đồng minh tại b6 (2, 1), sau đó là a7 (1, 0)
        val b6 = Position(2, 1); board[b6.row][b6.col] = Piece(PieceType.PAWN, PieceColor.WHITE)
        val a7 = Position(1, 0)

        // 6. UP-RIGHT (-1, 1): địch tại f6 (2, 5), sau đó là g7 (1, 6)
        val f6 = Position(2, 5); board[f6.row][f6.col] = Piece(PieceType.ROOK, PieceColor.BLACK)
        val g7 = Position(1, 6)

        // 7. DOWN-LEFT (1, -1): đồng minh tại b2 (6, 1), sau đó là a1 (7, 0)
        val b2 = Position(6, 1); board[b2.row][b2.col] = Piece(PieceType.BISHOP, PieceColor.WHITE)
        val a1 = Position(7, 0)

        // 8. DOWN-RIGHT (1, 1): địch tại f2 (6, 5), sau đó là g1 (7, 6)
        val f2 = Position(6, 5); board[f2.row][f2.col] = Piece(PieceType.KNIGHT, PieceColor.BLACK)
        val g1 = Position(7, 6)

        val state = GameState(board = board.map { it.toList() }, turn = PieceColor.WHITE)

        // Xác nhận isPathClear = false cho tất cả ô sau vật cản trên cả 8 hướng
        val squaresBehind = listOf(d7, d1, a4, g4, a7, g7, a1, g1)
        for (sq in squaresBehind) {
            assertFalse("isPathClear phải trả về false đối với ô $sq nằm sau vật cản", ChessEngine.isPathClear(state.board, d4, sq))
        }

        // Xác nhận getLegalMovesForPiece tuyệt đối không chứa bất kỳ ô nào sau vật cản
        val queenMoves = ChessEngine.getLegalMovesForPiece(state, d4).map { it.to }
        for (sq in squaresBehind) {
            assertFalse("Hậu tuyệt đối không thể đi tới ô $sq sau vật cản", sq in queenMoves)
        }

        // Không thể đi vào ô đồng minh
        val friendlyObstacles = listOf(d6, b4, b6, b2)
        for (sq in friendlyObstacles) {
            assertFalse("Hậu không thể đi vào ô quân đồng minh $sq", sq in queenMoves)
        }

        // Có thể ăn quân địch làm vật cản
        val enemyObstacles = listOf(d2, f4, f6, f2)
        for (sq in enemyObstacles) {
            assertTrue("Hậu phải ăn được quân địch làm vật cản ở $sq", sq in queenMoves)
        }

        // isStrictFideLegalMove trả về false cho tất cả nước đi nhảy qua
        for (sq in squaresBehind) {
            val illegalJump = ChessMove(from = d4, to = sq, piece = Piece(PieceType.QUEEN, PieceColor.WHITE))
            assertFalse(ChessEngine.isStrictFideLegalMove(state, illegalJump))
        }
    }

    @Test
    fun pinnedSlidingPiece_cannotLeavePinRayAndExposeKing() {
        val board = MutableList(8) { MutableList<Piece?>(8) { null } }
        // Vua trắng ở e1 (row=7, col=4)
        val e1 = Position(7, 4)
        board[e1.row][e1.col] = Piece(PieceType.KING, PieceColor.WHITE)

        // Vua đen ở a8 (row=0, col=0)
        board[0][0] = Piece(PieceType.KING, PieceColor.BLACK)

        // Xe đen ghim tuyệt đối trên cột e tại e8 (row=0, col=4)
        val e8 = Position(0, 4)
        board[e8.row][e8.col] = Piece(PieceType.ROOK, PieceColor.BLACK)

        // Xe trắng ở e4 (row=4, col=4) bị ghim dọc bảo vệ Vua
        val e4 = Position(4, 4)
        board[e4.row][e4.col] = Piece(PieceType.ROOK, PieceColor.WHITE)

        val state = GameState(board = board.map { it.toList() }, turn = PieceColor.WHITE)

        val rookMoves = ChessEngine.getLegalMovesForPiece(state, e4)
        val rookDestinations = rookMoves.map { it.to }

        // Xe trắng bị ghim:
        // CẤM rời khỏi cột e (ngang: d4, c4, f4, g4...)
        val illegalHorizontals = listOf(Position(4, 3), Position(4, 2), Position(4, 5), Position(4, 6))
        for (dest in illegalHorizontals) {
            assertFalse("Xe bị ghim tuyệt đối không được rời cột e sang $dest", dest in rookDestinations)
            val illegalMove = ChessMove(from = e4, to = dest, piece = Piece(PieceType.ROOK, PieceColor.WHITE))
            assertFalse("isStrictFideLegalMove phải từ chối nước đi làm hở Vua tới $dest", ChessEngine.isStrictFideLegalMove(state, illegalMove))
            val rejectedState = ChessEngine.applyMove(state, illegalMove)
            assertEquals("applyMove phải giữ nguyên trạng thái khi nước đi làm hở Vua", state, rejectedState)
        }

        // ĐƯỢC PHÉP đi dọc theo tia ghim trên cột e hoặc ăn quân Xe đen ở e8
        assertTrue("Xe được di chuyển dọc tia ghim lên e5", Position(3, 4) in rookDestinations)
        assertTrue("Xe được di chuyển dọc tia ghim lên e6", Position(2, 4) in rookDestinations)
        assertTrue("Xe được di chuyển dọc tia ghim lên e7", Position(1, 4) in rookDestinations)
        assertTrue("Xe được ăn quân Xe ghim ở e8", Position(0, 4) in rookDestinations)
        assertTrue("Xe được lùi dọc tia ghim về e3", Position(5, 4) in rookDestinations)
        assertTrue("Xe được lùi dọc tia ghim về e2", Position(6, 4) in rookDestinations)

        // KIỂM TRA THÊM: Tượng bị ghim chéo tuyệt đối
        val diagBoard = MutableList(8) { MutableList<Piece?>(8) { null } }
        diagBoard[e1.row][e1.col] = Piece(PieceType.KING, PieceColor.WHITE) // Vua ở e1 (7, 4)
        diagBoard[0][0] = Piece(PieceType.KING, PieceColor.BLACK) // Vua đen ở a8

        // Tượng đen ở b4 (row=4, col=1) ghim qua đường chéo e1-d2-c3-b4
        val b4 = Position(4, 1)
        diagBoard[b4.row][b4.col] = Piece(PieceType.BISHOP, PieceColor.BLACK)

        // Tượng trắng ở d2 (row=6, col=3) bị ghim chéo
        val d2 = Position(6, 3)
        diagBoard[d2.row][d2.col] = Piece(PieceType.BISHOP, PieceColor.WHITE)

        val diagState = GameState(board = diagBoard.map { it.toList() }, turn = PieceColor.WHITE)
        val bishopMoves = ChessEngine.getLegalMovesForPiece(diagState, d2).map { it.to }

        // Tượng không được rời khỏi đường chéo ghim (ví dụ d2->e3, d2->c1)
        assertFalse("Tượng bị ghim không được rời đường chéo ghim sang e3", Position(5, 4) in bishopMoves)
        assertFalse("Tượng bị ghim không được rời đường chéo ghim sang c1", Position(7, 2) in bishopMoves)

        // Tượng chỉ được đi dọc tia ghim: c3 và ăn Tượng đen ở b4
        assertTrue("Tượng được đi dọc tia ghim tới c3", Position(5, 2) in bishopMoves)
        assertTrue("Tượng được ăn quân Tượng đen đang ghim ở b4", Position(4, 1) in bishopMoves)
    }
}
