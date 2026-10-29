package com.harrylong.tvchess.ui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

enum class DeviceProfile {
    TV,
    PHONE,
    TABLET
}

/**
 * CompositionLocal cung cấp cấu hình thiết bị (TV / PHONE / TABLET)
 * cho toàn bộ cây giao diện Compose của "Đậu Học Cờ Vua".
 */
val LocalDeviceProfile = compositionLocalOf { DeviceProfile.TV }

/**
 * Nhận diện loại thiết bị đang chạy:
 * 1. TV: Nếu UiModeManager báo UI_MODE_TYPE_TELEVISION hoặc thiết bị hỗ trợ Leanback
 * 2. TABLET: Nếu smallestScreenWidthDp >= 600 (máy tính bảng / màn hình lớn)
 * 3. PHONE: Điện thoại di động
 */
fun detectDeviceProfile(context: Context, smallestWidthDp: Int): DeviceProfile {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val isTelevision = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    val hasLeanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    return when {
        isTelevision || hasLeanback -> DeviceProfile.TV
        smallestWidthDp >= 600 -> DeviceProfile.TABLET
        else -> DeviceProfile.PHONE
    }
}

@Composable
fun rememberDeviceProfile(): DeviceProfile {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val smallestWidthDp = configuration.smallestScreenWidthDp
    return remember(context, smallestWidthDp) {
        detectDeviceProfile(context, smallestWidthDp)
    }
}

@Composable
fun ProvideDeviceProfile(
    profile: DeviceProfile = rememberDeviceProfile(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalDeviceProfile provides profile) {
        content()
    }
}
