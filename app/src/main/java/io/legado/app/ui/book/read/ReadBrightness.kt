package io.legado.app.ui.book.read

import android.app.Activity
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean

/**
 * 阅读页亮度：跟随系统，或使用手动设定的亮度（1–255）
 */
object ReadBrightness {

    private const val KEY_AUTO = "brightnessAuto"

    fun isAuto(activity: Activity): Boolean = activity.getPrefBoolean(KEY_AUTO, true)

    fun setAuto(activity: Activity, auto: Boolean) {
        activity.putPrefBoolean(KEY_AUTO, auto)
        apply(activity)
    }

    /** 按当前设置调整窗口亮度；value 为手动亮度，跟随系统时忽略 */
    fun apply(activity: Activity, value: Int = AppConfig.readBrightness) {
        val params = activity.window.attributes
        params.screenBrightness = if (isAuto(activity)) {
            BRIGHTNESS_OVERRIDE_NONE
        } else {
            value.coerceIn(1, 255) / 255f
        }
        activity.window.attributes = params
    }
}
