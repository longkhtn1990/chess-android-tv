package com.harrylong.tvchess

import android.content.Context
import android.hardware.input.InputManager
import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.harrylong.tvchess.audio.BgmPlayer
import com.harrylong.tvchess.input.DareuGamepadController
import com.harrylong.tvchess.input.GamepadDeviceDetector
import com.harrylong.tvchess.input.GamepadEventBus
import com.harrylong.tvchess.input.isRecognizedTvChessKey
import com.harrylong.tvchess.ui.AppRoot

class MainActivity : ComponentActivity() {

    private var inputManager: InputManager? = null

    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            GamepadEventBus.refreshDeviceStatus()
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            GamepadEventBus.refreshDeviceStatus()
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            GamepadEventBus.refreshDeviceStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Đăng ký theo dõi cắm / ngắt kết nối tay cầm Dareu H101X (USB / 2.4G / Bluetooth)
        inputManager = getSystemService(Context.INPUT_SERVICE) as? InputManager
        inputManager?.registerInputDeviceListener(inputDeviceListener, null)
        GamepadEventBus.refreshDeviceStatus()

        setContent {
            AppRoot(
                onExitApp = { finish() }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        GamepadEventBus.refreshDeviceStatus()
        BgmPlayer.resume()
    }

    override fun onPause() {
        super.onPause()
        BgmPlayer.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        inputManager?.unregisterInputDeviceListener(inputDeviceListener)
        BgmPlayer.release()
    }

    /**
     * Bắt các tín hiệu chuyển động từ Cần Analog Trái / Phải, Cò Analog và D-Pad Hat
     * của tay cầm Dareu H101X và gửi tới GamepadEventBus.
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val action = DareuGamepadController.default.parseMotionEvent(event)
        if (action != null) {
            Log.d("TVCHESS_INPUT", "dispatchGenericMotionEvent action=$action source=${event.source}")
            GamepadEventBus.refreshDeviceStatus()
            GamepadEventBus.emitAction(action)
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    /**
     * Bắt toàn diện sự kiện bàn phím và nút bấm tay cầm Dareu H101X (bao gồm cả D-Pad, A, B, X, Y...)
     * cũng như Remote TV Xiaomi để chuyển đổi thành GamepadAction và phân phối qua GamepadEventBus.
     * Tiêu thụ sự kiện (return true) để ngăn Compose bị nhận sự kiện trùng lặp.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val isFromGamepad = GamepadDeviceDetector.isGamepadDevice(event.device) ||
                (event.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            val isRecognized = isRecognizedTvChessKey(event.keyCode)

            if (isFromGamepad || isRecognized) {
                val action = DareuGamepadController.default.parseKeyEvent(event)
                Log.d("TVCHESS_INPUT", "dispatchKeyEvent keyCode=${event.keyCode} action=$action isGamepad=$isFromGamepad isRecog=$isRecognized")
                if (action != null) {
                    GamepadEventBus.refreshDeviceStatus()
                    GamepadEventBus.emitAction(action)
                }
                return true
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            val isRecognized = isRecognizedTvChessKey(event.keyCode)
            if (isRecognized) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
