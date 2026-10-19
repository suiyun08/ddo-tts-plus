package io.legado.app.utils

import android.app.Dialog
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.core.view.forEach
import androidx.fragment.app.DialogFragment
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.theme.filletBackground
import splitties.systemservices.windowManager

fun AlertDialog.applyTint(): AlertDialog {
    window?.setBackgroundDrawable(context.filletBackground)
    val colorStateList = Selector.colorBuild()
        .setDefaultColor(ThemeStore.accentColor(context))
        .setPressedColor(ColorUtils.darkenColor(ThemeStore.accentColor(context)))
        .create()
    if (getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
        getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(colorStateList)
    }
    if (getButton(AlertDialog.BUTTON_POSITIVE) != null) {
        getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(colorStateList)
    }
    if (getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
        getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(colorStateList)
    }
    if (!AppConfig.isEInkMode) styleButtonsAsPills()
    window?.decorView?.post {
        listView?.forEach {
            it.applyTint(context.accentColor)
        }
    }
    return this
}

fun AlertDialog.requestInputMethod() {
    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
}

fun DialogFragment.setLayout(widthMix: Float, heightMix: Float) {
    dialog?.setLayout(widthMix, heightMix)
}

fun Dialog.setLayout(widthMix: Float, heightMix: Float) {
    val dm = context.windowManager.windowSize
    window?.setLayout(
        (dm.widthPixels * widthMix).toInt(),
        (dm.heightPixels * heightMix).toInt()
    )
}

fun DialogFragment.setLayout(width: Int, heightMix: Float) {
    dialog?.setLayout(width, heightMix)
}

fun Dialog.setLayout(width: Int, heightMix: Float) {
    val dm = context.windowManager.windowSize
    window?.setLayout(
        width,
        (dm.heightPixels * heightMix).toInt()
    )
}

fun DialogFragment.setLayout(widthMix: Float, height: Int) {
    dialog?.setLayout(widthMix, height)
}

fun Dialog.setLayout(widthMix: Float, height: Int) {
    val dm = context.windowManager.windowSize
    window?.setLayout(
        (dm.widthPixels * widthMix).toInt(),
        height
    )
}

fun DialogFragment.setLayout(width: Int, height: Int) {
    dialog?.setLayout(width, height)
}

fun Dialog.setLayout(width: Int, height: Int) {
    window?.setLayout(width, height)
}

/**
 * 对话框按钮改为底部等宽并排的胶囊按钮：确定为强调色填充，其余为浅灰底
 */
private fun AlertDialog.styleButtonsAsPills() {
    val accent = ThemeStore.accentColor(context)
    val textColor = context.getPrimaryTextColor(ColorUtils.isColorLight(context.backgroundColor))
    val positive = getButton(AlertDialog.BUTTON_POSITIVE)
    val buttons = listOf(
        getButton(AlertDialog.BUTTON_NEUTRAL),
        getButton(AlertDialog.BUTTON_NEGATIVE),
        positive
    ).filterNotNull().filter { it.visibility == View.VISIBLE && !it.text.isNullOrEmpty() }
    val bar = buttons.firstOrNull()?.parent as? LinearLayout ?: return
    // 隐藏按钮之间的占位，让按钮等宽并排
    bar.findViewById<View>(androidx.appcompat.R.id.spacer)?.visibility = View.GONE
    bar.setPadding(16.dpToPx(), 4.dpToPx(), 16.dpToPx(), 16.dpToPx())
    buttons.forEach { button ->
        val isPositive = button == positive
        val bg = if (isPositive) accent else ColorUtils.withAlpha(textColor, 0.08f)
        button.background = Selector.shapeBuild()
            .setCornerRadius(22.dpToPx())
            .setDefaultBgColor(bg)
            .setPressedBgColor(ColorUtils.darkenColor(bg))
            .create()
        button.setTextColor(
            if (!isPositive) textColor
            else if (ColorUtils.isColorLight(accent)) Color.BLACK else Color.WHITE
        )
        button.isAllCaps = false
        button.minHeight = 44.dpToPx()
        button.minimumHeight = 44.dpToPx()
        (button.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            lp.width = 0
            lp.weight = 1f
            lp.marginStart = 5.dpToPx()
            lp.marginEnd = 5.dpToPx()
            button.layoutParams = lp
        }
    }
}

