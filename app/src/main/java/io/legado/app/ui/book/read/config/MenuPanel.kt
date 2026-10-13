package io.legado.app.ui.book.read.config

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.view.doOnLayout
import androidx.core.view.forEach
import androidx.fragment.app.DialogFragment
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx

/**
 * 底部菜单的二级面板（朗读、界面、设置）：
 * - 贴在底部菜单栏上方显示，不遮挡菜单栏；
 * - 面板外的触摸交给阅读页：点底部按钮直接切换面板，点空白处收起面板；
 * - 高度随内容伸缩，最高为屏幕高度的 [maxHeightRatio]，超出部分在面板内滚动。
 * 在对话框 onStart 中、设置好窗口属性之后调用。
 *
 * @param fixedHeight 为 true 时固定为最大高度（内容是列表、无法按内容测量高度时使用）
 */
fun DialogFragment.placeAboveReadMenu(
    maxHeightRatio: Float = 0.7f,
    fixedHeight: Boolean = false
) {
    val readActivity = activity as? ReadBookActivity ?: return
    val window = dialog?.window ?: return
    window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    // 去掉对话框窗口自带的阴影（会盖到底部菜单栏上），改为顶部圆角，与菜单栏之间由菜单栏顶部的分隔线区分
    window.setElevation(0f)
    view?.let { root ->
        val color = (root.background as? ColorDrawable)?.color
        if (color != null) {
            val radius = 16.dpToPx().toFloat()
            root.background = GradientDrawable().apply {
                setColor(color)
                cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            }
            root.clipToOutline = true
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }
    val maxHeight = (resources.displayMetrics.heightPixels * maxHeightRatio).toInt()
    if (fixedHeight) {
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight)
    }
    val decor = window.decorView
    decor.doOnLayout {
        val menuTop = readActivity.bottomMenuTopOnScreen() ?: return@doOnLayout
        val location = IntArray(2)
        decor.getLocationOnScreen(location)
        val attr = window.attributes
        // 让面板底边对齐菜单栏顶边
        attr.y += location[1] + decor.height - menuTop
        window.attributes = attr
        if (!fixedHeight && decor.height > maxHeight) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight)
        }
    }
}

/**
 * 阅读菜单面板里的控件样式：无描边的圆角色块，按下变深，选中为强调色
 */
object MenuPanelStyle {

    /** 色块按钮（字体、缩进、边距等） */
    fun tile(textColor: Int, radius: Int = 12): Drawable {
        return Selector.shapeBuild()
            .setCornerRadius(radius.dpToPx())
            .setDefaultBgColor(ColorUtils.withAlpha(textColor, 0.07f))
            .setPressedBgColor(ColorUtils.withAlpha(textColor, 0.15f))
            .create()
    }

    /** 分段选择中的一项：选中时填充强调色 */
    fun segment(context: Context, textColor: Int): Drawable {
        return Selector.shapeBuild()
            .setCornerRadius(10.dpToPx())
            .setDefaultBgColor(ColorUtils.withAlpha(textColor, 0.07f))
            .setPressedBgColor(ColorUtils.withAlpha(textColor, 0.15f))
            .setCheckedBgColor(context.accentColor)
            .create()
    }

    /** 给 RadioGroup 里的每一项套用分段样式 */
    fun applySegment(group: RadioGroup, textColor: Int) {
        group.forEach {
            it.background = segment(group.context, textColor)
            (it as? TextView)?.minHeight = 40.dpToPx()
        }
    }
}
