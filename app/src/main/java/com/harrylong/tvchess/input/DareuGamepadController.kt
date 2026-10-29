package com.harrylong.tvchess.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.harrylong.tvchess.model.DPadDirection
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/**
 * Các hành vi nhận dạng từ Tay cầm Dareu H101X và Remote TV.
 * Thiết kế cho bé Đậu với trải nghiệm trực quan, nhạy bén và không bị loạn phím.
 */
sealed interface GamepadAction {
    /**
     * Di chuyển con trỏ trên bàn cờ hoặc trong các menu.
     * [forceFreeGrid] = true khi bé gạt cần Analog Phải để di chuyển tự do từng ô bất cứ lúc nào.
     */
    data class Navigate(val direction: DPadDirection, val forceFreeGrid: Boolean = false) : GamepadAction

    /** Nút A (Xanh lá) / OK / DPAD_CENTER / ENTER: Chọn quân cờ / Đánh nước đi */
    data object PrimaryConfirmA : GamepadAction

    /** Nút B (Đỏ) / BACK / ESCAPE: Hủy chọn quân / Quay lại */
    data object SecondaryCancelB : GamepadAction

    /** Nút X (Xanh dương): Bóng đèn mách nước nhanh cho bé */
    data object QuickHintX : GamepadAction

    /** Nút Y (Vàng): Đi lại nước vừa rồi nhanh */
    data object QuickUndoY : GamepadAction

    /** Nút L1 / LB: Chuyển đổi qua lại giữa chế độ Nhảy thông minh ⚡ và Đi từng ô 🎯 */
    data object ToggleNavModeLB : GamepadAction

    /** Nút R1 / RB: Chuyển nhanh sang quân cờ kế tiếp của bé */
    data object CyclePieceRB : GamepadAction

    /** Nút Cò L2 / R2 / Trigger: Nghe giọng đọc vui nhộn hướng dẫn quân cờ đang trỏ */
    data object SpeakPieceInfoTrigger : GamepadAction

    /** Nút START / Menu: Mở hoặc đóng bảng điều khiển bên phải */
    data object ToggleMenuStart : GamepadAction

    /** Nút SELECT / Mode: Đổi nhanh bạn Quái Thú (Khủng Long / King Kong / Godzilla) */
    data object SwitchMonsterSelect : GamepadAction
}

/**
 * Dữ liệu trực quan biểu diễn nút bấm tay cầm Dareu H101X để hiển thị gợi ý trên TV.
 */
data class GamepadShortcutHint(
    val buttonLabel: String,
    val actionTitle: String,
    val colorHex: Long,
    val iconEmoji: String
)

/**
 * Bộ xử lý toàn diện cho tay cầm Dareu H101X (USB có dây, Receiver 2.4G và Bluetooth),
 * hỗ trợ cả 2 chế độ XInput, DirectInput và tương thích 100% với Remote Xiaomi TV.
 */
class DareuGamepadController(
    val dpadRepeatCooldownMs: Long = 150L,
    val hatRepeatIntervalMs: Long = 165L,
    val leftStickCooldownMs: Long = 180L,
    val rightStickCooldownMs: Long = 180L,
    val leftStickDeadzone: Float = 0.38f,
    val rightStickDeadzone: Float = 0.42f,
    val hatThreshold: Float = 0.5f,
    val triggerThreshold: Float = 0.6f,
    val buttonDebounceMs: Long = 180L
) {
    // Trạng thái chống lặp cho DPad KeyEvent
    private var lastDpadKeyTimestampMs: Long = 0L
    private var lastDpadKeyDirection: DPadDirection? = null

    // Chống ăn đúp (double-trigger) cho các nút bấm chức năng (A, B, X, Y...)
    private var lastActionButtonTimestampMs: Long = 0L
    private var lastActionButtonCode: Int = -1

    // Trạng thái cụm DPad Hat (AXIS_HAT_X / AXIS_HAT_Y)
    private var lastHatDirection: DPadDirection? = null
    private var lastHatTriggerTimeMs: Long = 0L

    // Trạng thái cần Analog Trái (AXIS_X / AXIS_Y)
    private var leftStickNeutral: Boolean = true
    private var lastLeftStickDir: DPadDirection? = null
    private var lastLeftStickTriggerTimeMs: Long = 0L

    // Trạng thái cần Analog Phải (AXIS_Z / AXIS_RZ)
    private var rightStickNeutral: Boolean = true
    private var lastRightStickDir: DPadDirection? = null
    private var lastRightStickTriggerTimeMs: Long = 0L

    // Trạng thái cò Analog L2 / R2 (Edge-triggered)
    private var isTriggerEngaged: Boolean = false

    /**
     * Xóa sạch trạng thái đếm thời gian và vị trí cần gạt (hữu ích khi đổi ván mới hoặc reset).
     */
    fun resetState() {
        lastDpadKeyTimestampMs = 0L
        lastDpadKeyDirection = null
        lastActionButtonTimestampMs = 0L
        lastActionButtonCode = -1
        lastHatDirection = null
        lastHatTriggerTimeMs = 0L
        leftStickNeutral = true
        lastLeftStickDir = null
        lastLeftStickTriggerTimeMs = 0L
        rightStickNeutral = true
        lastRightStickDir = null
        lastRightStickTriggerTimeMs = 0L
        isTriggerEngaged = false
    }

    /**
     * Hàm thuần túy kiểm tra và chuyển đổi KeyCode sang GamepadAction.
     * Tách biệt độc lập khỏi android.view để phục vụ Unit Test trên JVM.
     */
    fun mapKeyCodeToAction(
        keyCode: Int,
        repeatCount: Int = 0,
        nowMs: Long = System.currentTimeMillis()
    ): GamepadAction? {
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP -> DPadDirection.UP

            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN -> DPadDirection.DOWN

            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> DPadDirection.LEFT

            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> DPadDirection.RIGHT

            else -> null
        }

        if (direction != null) {
            return if (repeatCount == 0 || direction != lastDpadKeyDirection) {
                lastDpadKeyDirection = direction
                lastDpadKeyTimestampMs = nowMs
                GamepadAction.Navigate(direction)
            } else {
                if (nowMs - lastDpadKeyTimestampMs >= dpadRepeatCooldownMs) {
                    lastDpadKeyTimestampMs = nowMs
                    GamepadAction.Navigate(direction)
                } else {
                    null
                }
            }
        }

        // Với tất cả các nút chức năng, bỏ qua lặp phím khi bé ấn giữ nút
        if (repeatCount > 0) return null

        // Chống ăn đúp (double-trigger) cho nút bấm chức năng trong khoảng debounce
        if (keyCode == lastActionButtonCode && (nowMs - lastActionButtonTimestampMs < buttonDebounceMs)) {
            return null
        }

        val action = when (keyCode) {
            // Nút A / Enter / Chọn
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_BUTTON_2 -> GamepadAction.PrimaryConfirmA

            // Nút B / Back / Hủy
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_BUTTON_3 -> GamepadAction.SecondaryCancelB

            // Nút X: Mách nước nhanh
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_1 -> GamepadAction.QuickHintX

            // Nút Y: Đi lại nhanh
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_4 -> GamepadAction.QuickUndoY

            // Nút L1 / LB: Đổi kiểu di chuyển Nhảy thông minh ↔ Từng ô
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_5 -> GamepadAction.ToggleNavModeLB

            // Nút R1 / RB: Đổi nhanh sang quân cờ kế tiếp
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_6 -> GamepadAction.CyclePieceRB

            // Cò L2 / R2 dạng nút: Nghe đọc tên và cách đi của quân cờ
            KeyEvent.KEYCODE_BUTTON_L2,
            KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_7,
            KeyEvent.KEYCODE_BUTTON_8 -> GamepadAction.SpeakPieceInfoTrigger

            // Nút START / Menu: Mở bảng Menu bên phải
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_BUTTON_10 -> GamepadAction.ToggleMenuStart

            // Nút SELECT / MODE: Đổi nhanh bạn Quái Thú
            KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_MODE,
            KeyEvent.KEYCODE_BUTTON_9 -> GamepadAction.SwitchMonsterSelect

            else -> null
        }

        if (action != null) {
            lastActionButtonCode = keyCode
            lastActionButtonTimestampMs = nowMs
        }
        return action
    }

    /**
     * Xử lý sự kiện bàn phím / nút bấm tay cầm [KeyEvent].
     */
    fun parseKeyEvent(event: KeyEvent): GamepadAction? {
        if (event.action != KeyEvent.ACTION_DOWN) return null
        val eventTime = if (event.eventTime > 0) event.eventTime else System.currentTimeMillis()
        return mapKeyCodeToAction(event.keyCode, event.repeatCount, eventTime)
    }

    /**
     * Hàm thuần túy đánh giá toạ độ các cần Analog, D-Pad Hat và Cò Analog.
     * Tách biệt logic độc lập khỏi Android MotionEvent để Unit Test 100% trên JVM.
     */
    fun evaluateStickAndHat(
        hatX: Float = 0f,
        hatY: Float = 0f,
        leftX: Float = 0f,
        leftY: Float = 0f,
        rightX: Float = 0f,
        rightY: Float = 0f,
        lTrigger: Float = 0f,
        rTrigger: Float = 0f,
        nowMs: Long = System.currentTimeMillis()
    ): GamepadAction? {
        // 1. Cò Analog L2 / R2 (Edge-triggered: chỉ phát 1 lần khi nhấn sâu quá ngưỡng)
        val isTriggerPulled = lTrigger >= triggerThreshold || rTrigger >= triggerThreshold
        if (isTriggerPulled) {
            if (!isTriggerEngaged) {
                isTriggerEngaged = true
                return GamepadAction.SpeakPieceInfoTrigger
            }
        } else {
            isTriggerEngaged = false
        }

        // 2. Cụm D-Pad Hat (Dareu H101X gửi tín hiệu D-Pad dạng AXIS_HAT_X / AXIS_HAT_Y)
        val currentHatDir: DPadDirection? = when {
            hatY < -hatThreshold -> DPadDirection.UP
            hatY > hatThreshold -> DPadDirection.DOWN
            hatX < -hatThreshold -> DPadDirection.LEFT
            hatX > hatThreshold -> DPadDirection.RIGHT
            else -> null
        }

        if (currentHatDir != null) {
            if (currentHatDir != lastHatDirection) {
                lastHatDirection = currentHatDir
                lastHatTriggerTimeMs = nowMs
                return GamepadAction.Navigate(currentHatDir, forceFreeGrid = false)
            } else if (nowMs - lastHatTriggerTimeMs >= hatRepeatIntervalMs) {
                lastHatTriggerTimeMs = nowMs
                return GamepadAction.Navigate(currentHatDir, forceFreeGrid = false)
            }
            // Đang giữ Hat nhưng chưa đến chu kỳ lặp kế tiếp
            return null
        } else {
            lastHatDirection = null
        }

        // 3. Cần Analog Phải (AXIS_Z / AXIS_RZ): Di chuyển tự do từng ô bất kỳ lúc nào
        val rightAbsX = abs(rightX)
        val rightAbsY = abs(rightY)
        val isRightActive = rightAbsX >= rightStickDeadzone || rightAbsY >= rightStickDeadzone

        if (isRightActive) {
            val rightDir = if (rightAbsX > rightAbsY) {
                if (rightX > 0f) DPadDirection.RIGHT else DPadDirection.LEFT
            } else {
                if (rightY > 0f) DPadDirection.DOWN else DPadDirection.UP
            }

            if (rightStickNeutral || rightDir != lastRightStickDir || (nowMs - lastRightStickTriggerTimeMs >= rightStickCooldownMs)) {
                rightStickNeutral = false
                lastRightStickDir = rightDir
                lastRightStickTriggerTimeMs = nowMs
                return GamepadAction.Navigate(rightDir, forceFreeGrid = true)
            }
            return null
        } else {
            rightStickNeutral = true
            lastRightStickDir = null
        }

        // 4. Cần Analog Trái (AXIS_X / AXIS_Y): Điều hướng tiêu chuẩn
        val leftAbsX = abs(leftX)
        val leftAbsY = abs(leftY)
        val isLeftActive = leftAbsX >= leftStickDeadzone || leftAbsY >= leftStickDeadzone

        if (isLeftActive) {
            val leftDir = if (leftAbsX > leftAbsY) {
                if (leftX > 0f) DPadDirection.RIGHT else DPadDirection.LEFT
            } else {
                if (leftY > 0f) DPadDirection.DOWN else DPadDirection.UP
            }

            if (leftStickNeutral || leftDir != lastLeftStickDir || (nowMs - lastLeftStickTriggerTimeMs >= leftStickCooldownMs)) {
                leftStickNeutral = false
                lastLeftStickDir = leftDir
                lastLeftStickTriggerTimeMs = nowMs
                return GamepadAction.Navigate(leftDir, forceFreeGrid = false)
            }
            return null
        } else {
            leftStickNeutral = true
            lastLeftStickDir = null
        }

        return null
    }

    /**
     * Xử lý tín hiệu chuyển động cần analog, d-pad hat hoặc cò analog [MotionEvent].
     */
    fun parseMotionEvent(
        event: MotionEvent,
        currentTimeMs: Long = System.currentTimeMillis()
    ): GamepadAction? {
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val leftX = event.getAxisValue(MotionEvent.AXIS_X)
        val leftY = event.getAxisValue(MotionEvent.AXIS_Y)
        val rightX = event.getAxisValue(MotionEvent.AXIS_Z)
        val rightY = event.getAxisValue(MotionEvent.AXIS_RZ)

        val lTrigger = maxOf(
            event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_BRAKE)
        )
        val rTrigger = maxOf(
            event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_GAS)
        )

        val now = if (event.eventTime > 0) event.eventTime else currentTimeMs
        return evaluateStickAndHat(
            hatX = hatX,
            hatY = hatY,
            leftX = leftX,
            leftY = leftY,
            rightX = rightX,
            rightY = rightY,
            lTrigger = lTrigger,
            rTrigger = rTrigger,
            nowMs = now
        )
    }

    companion object {
        /** Instance mặc định tiện dùng cho toàn bộ ứng dụng */
        val default = DareuGamepadController()

        /**
         * Danh sách các biểu tượng và phím tắt của tay cầm Dareu H101X
         * để hiển thị trên màn hình TV cho bé dễ nhìn theo dõi.
         */
        fun getDareuShortcutHints(): List<GamepadShortcutHint> = listOf(
            GamepadShortcutHint("A", "Chọn / Đi cờ", 0xFF22C55EL, "🟢"),
            GamepadShortcutHint("B", "Hủy / Quay lại", 0xFFEF4444L, "🔴"),
            GamepadShortcutHint("X", "Mách nước", 0xFF3B82F6L, "💡"),
            GamepadShortcutHint("Y", "Đi lại nhanh", 0xFFF59E0BL, "↩️"),
            GamepadShortcutHint("LB", "Đổi kiểu đi", 0xFF8B5CF6L, "⚡"),
            GamepadShortcutHint("RB", "Quân kế tiếp", 0xFFEC4899L, "⏭️"),
            GamepadShortcutHint("LT/RT", "Đọc tên quân", 0xFF14B8A6L, "🔊"),
            GamepadShortcutHint("START", "Mở Menu", 0xFF6366F1L, "📋"),
            GamepadShortcutHint("SELECT", "Đổi Quái Thú", 0xFFF97316L, "🦖")
        )
    }
}

/**
 * Bộ nhận diện thiết bị tay cầm kết nối vào TV (Dareu H101X, Gamepad không dây, USB...).
 */
object GamepadDeviceDetector {

    /**
     * Kiểm tra một [InputDevice] có phải là thiết bị chơi game (Gamepad hoặc Joystick) hay không.
     */
    fun isGamepadDevice(device: InputDevice?): Boolean {
        if (device == null || device.isVirtual) return false
        val sources = device.sources
        val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        val isJoystick = (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        return isGamepad || isJoystick
    }

    /**
     * Nhận diện tay cầm Dareu H101X hoặc các dòng tay cầm phổ biến.
     */
    fun isDareuController(device: InputDevice?): Boolean {
        if (device == null) return false
        val name = device.name.lowercase()
        return name.contains("dareu") ||
            name.contains("h101") ||
            name.contains("101x") ||
            name.contains("gamepad") ||
            name.contains("xbox") ||
            name.contains("wireless controller")
    }

    /**
     * Lấy danh sách tất cả các tay cầm đang kết nối vào TV.
     */
    fun getConnectedGamepads(): List<InputDevice> {
        val gamepads = mutableListOf<InputDevice>()
        val deviceIds = InputDevice.getDeviceIds() ?: return emptyList()
        for (id in deviceIds) {
            val device = InputDevice.getDevice(id) ?: continue
            if (isGamepadDevice(device)) {
                gamepads.add(device)
            }
        }
        return gamepads
    }

    /**
     * Kiểm tra có bất kỳ tay cầm nào đang kết nối không.
     */
    fun isAnyGamepadConnected(): Boolean = getConnectedGamepads().isNotEmpty()

    /**
     * Trả về tên hiển thị thân thiện của tay cầm (ưu tiên Dareu H101X nếu phát hiện).
     */
    fun getConnectedGamepadDisplayName(): String? {
        val gamepads = getConnectedGamepads()
        if (gamepads.isEmpty()) return null
        val dareu = gamepads.firstOrNull { isDareuController(it) }
        return dareu?.name ?: gamepads.first().name
    }
}

/**
 * Kiểm tra nhanh xem mã [keyCode] có thuộc cụm phím nút bấm của tay cầm chơi game hay không.
 */
fun isGamepadButtonKeyCode(keyCode: Int): Boolean = when (keyCode) {
    KeyEvent.KEYCODE_BUTTON_A,
    KeyEvent.KEYCODE_BUTTON_B,
    KeyEvent.KEYCODE_BUTTON_C,
    KeyEvent.KEYCODE_BUTTON_X,
    KeyEvent.KEYCODE_BUTTON_Y,
    KeyEvent.KEYCODE_BUTTON_Z,
    KeyEvent.KEYCODE_BUTTON_L1,
    KeyEvent.KEYCODE_BUTTON_R1,
    KeyEvent.KEYCODE_BUTTON_L2,
    KeyEvent.KEYCODE_BUTTON_R2,
    KeyEvent.KEYCODE_BUTTON_THUMBL,
    KeyEvent.KEYCODE_BUTTON_THUMBR,
    KeyEvent.KEYCODE_BUTTON_START,
    KeyEvent.KEYCODE_BUTTON_SELECT,
    KeyEvent.KEYCODE_BUTTON_MODE,
    KeyEvent.KEYCODE_BUTTON_1,
    KeyEvent.KEYCODE_BUTTON_2,
    KeyEvent.KEYCODE_BUTTON_3,
    KeyEvent.KEYCODE_BUTTON_4,
    KeyEvent.KEYCODE_BUTTON_5,
    KeyEvent.KEYCODE_BUTTON_6,
    KeyEvent.KEYCODE_BUTTON_7,
    KeyEvent.KEYCODE_BUTTON_8,
    KeyEvent.KEYCODE_BUTTON_9,
    KeyEvent.KEYCODE_BUTTON_10,
    KeyEvent.KEYCODE_BUTTON_11,
    KeyEvent.KEYCODE_BUTTON_12,
    KeyEvent.KEYCODE_BUTTON_13,
    KeyEvent.KEYCODE_BUTTON_14,
    KeyEvent.KEYCODE_BUTTON_15,
    KeyEvent.KEYCODE_BUTTON_16 -> true
    else -> false
}

/**
 * Kiểm tra xem [keyCode] có phải là phím điều khiển hợp lệ của cờ vua
 * (gồm cả phím Gamepad Dareu H101X và phím điều hướng Remote TV / Bàn phím) hay không.
 */
fun isRecognizedTvChessKey(keyCode: Int): Boolean = isGamepadButtonKeyCode(keyCode) || when (keyCode) {
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP,
    KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN,
    KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT,
    KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT,
    KeyEvent.KEYCODE_ENTER,
    KeyEvent.KEYCODE_NUMPAD_ENTER,
    KeyEvent.KEYCODE_SPACE,
    KeyEvent.KEYCODE_BACK,
    KeyEvent.KEYCODE_ESCAPE,
    KeyEvent.KEYCODE_MENU -> true
    else -> false
}

/**
 * Event Bus trung tâm phân phối các tín hiệu từ tay cầm Dareu H101X
 * giữa MainActivity và các Composable (như TvChessScreen).
 */
object GamepadEventBus {
    private val _actions = MutableSharedFlow<GamepadAction>(extraBufferCapacity = 32)
    val actions: SharedFlow<GamepadAction> = _actions

    private val _isGamepadConnected = MutableStateFlow(false)
    val isGamepadConnected: StateFlow<Boolean> = _isGamepadConnected

    private val _gamepadName = MutableStateFlow<String?>(null)
    val gamepadName: StateFlow<String?> = _gamepadName

    fun emitAction(action: GamepadAction): Boolean = _actions.tryEmit(action)

    fun refreshDeviceStatus() {
        val connected = GamepadDeviceDetector.isAnyGamepadConnected()
        _isGamepadConnected.value = connected
        _gamepadName.value = GamepadDeviceDetector.getConnectedGamepadDisplayName()
    }
}
