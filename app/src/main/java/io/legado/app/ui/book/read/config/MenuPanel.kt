package io.legado.app.ui.book.read.config

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.view.doOnLayout
import androidx.core.view.forEach
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx

/*
 * 阅读菜单的面板与弹层：
 * - 二级面板（朗读、界面、设置、自动翻页）贴在底部菜单栏上方，见 [placeAboveReadMenu]
 * - 三级弹层（字体、边距、页眉页脚、各种选择列表等）为浮在菜单栏上方的圆角卡片，见 [styleAsMenuSheet]
 * - 控件统一为无描边圆角色块，列表分组画成圆角卡片，见 [MenuPanelStyle]、[GroupCardDecoration]
 */

/** 阅读菜单面板的文字颜色（跟随底部背景深浅） */
val Context.menuTextColor: Int
    get() = getPrimaryTextColor(ColorUtils.isColorLight(bottomBackground))

/**
 * 底部菜单的二级面板：
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
    roundCorners(window, topOnly = true)
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
 * 三级弹层：左右留边的圆角卡片，浮在底部菜单栏上方（菜单未显示时贴近屏幕底部），
 * 背后轻微变暗；高度随内容伸缩，最高为屏幕高度的 [maxHeightRatio]，超出部分在卡片内滚动。
 * 点卡片外关闭本层，下面的二级面板保留。在对话框 onStart 中调用。
 */
fun DialogFragment.styleAsMenuSheet(
    maxHeightRatio: Float = 0.75f,
    fixedHeight: Boolean = false
) {
    val window = dialog?.window ?: return
    val readActivity = activity as? ReadBookActivity
    val metrics = resources.displayMetrics
    val margin = 10.dpToPx()
    val width = metrics.widthPixels - margin * 2
    val maxHeight = (metrics.heightPixels * maxHeightRatio).toInt()
    window.decorView.setPadding(0, 0, 0, 0)
    window.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    window.setDimAmount(0.2f)
    roundCorners(window, topOnly = false)
    window.setLayout(width, if (fixedHeight) maxHeight else ViewGroup.LayoutParams.WRAP_CONTENT)
    val attr = window.attributes
    attr.y = margin
    window.attributes = attr
    val decor = window.decorView
    decor.doOnLayout {
        val location = IntArray(2)
        decor.getLocationOnScreen(location)
        // 浮在菜单栏上方，留出间距；高度不超过菜单栏上方的可用空间
        val bottomLimit = (readActivity?.bottomMenuTopOnScreen() ?: metrics.heightPixels) - margin
        val available = bottomLimit - 48.dpToPx()
        val newAttr = window.attributes
        newAttr.y += location[1] + decor.height - bottomLimit
        window.attributes = newAttr
        val limit = minOf(maxHeight, available)
        if (fixedHeight || decor.height > limit) {
            window.setLayout(width, limit)
        }
    }
}

/** 把对话框内容的纯色背景换成圆角背景，窗口背景透明 */
private fun DialogFragment.roundCorners(window: Window, topOnly: Boolean) {
    val root = view ?: return
    val color = (root.background as? ColorDrawable)?.color ?: requireContext().bottomBackground
    val radius = 16.dpToPx().toFloat()
    root.background = GradientDrawable().apply {
        setColor(color)
        cornerRadii = if (topOnly) {
            floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
        } else {
            FloatArray(8) { radius }
        }
    }
    root.clipToOutline = true
    window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
}

/**
 * 三级弹层的标题栏：标题 + 关闭按钮（关闭本层，回到下面的面板）
 */
fun DialogFragment.bindSheetHeader(header: View, title: CharSequence) {
    val textColor = requireContext().menuTextColor
    header.findViewById<TextView>(R.id.tv_sheet_title)?.apply {
        text = title
        setTextColor(textColor)
    }
    header.findViewById<ImageView>(R.id.iv_sheet_close)?.apply {
        setColorFilter(textColor)
        setOnClickListener { dismissAllowingStateLoss() }
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

    /** 选中状态的胶囊（如「跟随系统」开启时） */
    fun chipOn(context: Context, radius: Int = 15): Drawable {
        return Selector.shapeBuild()
            .setCornerRadius(radius.dpToPx())
            .setDefaultBgColor(ColorUtils.withAlpha(context.accentColor, 0.14f))
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

    /** 把一组列表行（LinearLayout 里的各行）画成一张圆角卡片，行间加分隔线 */
    fun applyGroupCard(group: ViewGroup, textColor: Int) {
        group.background = tile(textColor)
        group.clipToOutline = true
        val rows = (0 until group.childCount).map { group.getChildAt(it) }.filter { it.visibility != View.GONE }
        rows.forEachIndexed { index, row ->
            row.minimumHeight = 52.dpToPx()
            if (index < rows.lastIndex && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                row.foreground = BottomLineDrawable(ColorUtils.withAlpha(textColor, 0.1f), 16.dpToPx())
            }
        }
    }
}

/** 底部一条细线（左右缩进），用于列表行之间的分隔 */
private class BottomLineDrawable(color: Int, private val inset: Int) : Drawable() {
    private val paint = Paint().apply { this.color = color }

    override fun draw(canvas: Canvas) {
        val b = bounds
        canvas.drawRect(
            (b.left + inset).toFloat(), (b.bottom - 1).toFloat(),
            (b.right - inset).toFloat(), b.bottom.toFloat(), paint
        )
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}

/**
 * 把设置列表中每个分组的设置项画成一张圆角卡片，组内各项之间加分隔线
 */
@SuppressLint("RestrictedApi")
class GroupCardDecoration(textColor: Int) : RecyclerView.ItemDecoration() {

    private val margin = 12.dpToPx()
    private val lineInset = 16.dpToPx().toFloat()
    private val radius = 12.dpToPx().toFloat()
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.withAlpha(textColor, 0.06f)
    }
    private val linePaint = Paint().apply {
        color = ColorUtils.withAlpha(textColor, 0.1f)
        strokeWidth = 1f
    }
    private val path = Path()
    private val rect = RectF()

    /** 该位置是分组标题，或已超出列表范围 */
    private fun isBoundary(parent: RecyclerView, position: Int): Boolean {
        val adapter = parent.adapter as? PreferenceGroupAdapter ?: return true
        if (position < 0 || position >= adapter.itemCount) return true
        return adapter.getItem(position) is PreferenceGroup
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION || isBoundary(parent, position)) return
        outRect.left = margin
        outRect.right = margin
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION || isBoundary(parent, position)) continue
            val first = isBoundary(parent, position - 1)
            val last = isBoundary(parent, position + 1)
            rect.set(
                child.left.toFloat(),
                child.top + child.translationY,
                child.right.toFloat(),
                child.bottom + child.translationY
            )
            val top = if (first) radius else 0f
            val bottom = if (last) radius else 0f
            path.reset()
            path.addRoundRect(
                rect,
                floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom),
                Path.Direction.CW
            )
            c.drawPath(path, cardPaint)
            if (!last) {
                c.drawLine(rect.left + lineInset, rect.bottom, rect.right - lineInset, rect.bottom, linePaint)
            }
        }
    }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is FragmentActivity) return context
        context = context.baseContext
    }
    return null
}

/**
 * 阅读菜单里的选择列表：带 ✓ 的圆角卡片弹层，点选即生效并关闭，回到上一层。
 * 非 FragmentActivity 环境下退回系统选择框。
 *
 * @param checkedIndex 当前选中项，-1 表示不标记
 */
fun Context.menuSelector(
    title: CharSequence? = null,
    items: List<CharSequence>,
    checkedIndex: Int = -1,
    onPick: (Int) -> Unit
) {
    val activity = findFragmentActivity()
    if (activity == null) {
        if (title != null) selector(title, items) { _, i -> onPick(i) }
        else selector(items) { _, i -> onPick(i) }
        return
    }
    MenuOptionDialog().apply {
        arguments = Bundle().apply {
            putCharSequence("title", title)
            putCharSequenceArrayList("items", ArrayList(items))
            putInt("checked", checkedIndex)
        }
        this.onPick = onPick
    }.show(activity.supportFragmentManager, "menuOption")
}

/**
 * 选择列表弹层，见 [menuSelector]
 */
class MenuOptionDialog : BaseDialogFragment(R.layout.dialog_menu_option) {

    var onPick: ((Int) -> Unit)? = null

    override fun onStart() {
        super.onStart()
        styleAsMenuSheet()
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val pick = onPick
        val items = arguments?.getCharSequenceArrayList("items")
        // 页面重建后回调已丢失，直接关闭
        if (pick == null || items == null) {
            dismissAllowingStateLoss()
            return
        }
        val context = requireContext()
        val textColor = context.menuTextColor
        view.setBackgroundColor(context.bottomBackground)
        bindSheetHeader(view.findViewById(R.id.sheet_header), arguments?.getCharSequence("title") ?: "")
        val checked = arguments?.getInt("checked", -1) ?: -1
        val list = view.findViewById<LinearLayout>(R.id.ll_options)
        items.forEachIndexed { index, item ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(16.dpToPx(), 0, 12.dpToPx(), 0)
                setOnClickListener {
                    dismissAllowingStateLoss()
                    pick(index)
                }
            }
            row.addView(TextView(context).apply {
                text = item
                setTextColor(if (index == checked) context.accentColor else textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (index == checked) {
                row.addView(ImageView(context).apply {
                    setImageResource(R.drawable.ic_check)
                    setColorFilter(context.accentColor)
                }, LinearLayout.LayoutParams(20.dpToPx(), 20.dpToPx()))
            }
            list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52.dpToPx()))
        }
        MenuPanelStyle.applyGroupCard(list, textColor)
        // 当前选中项滚到可见处
        if (checked > 0) {
            view.findViewById<NestedScrollView>(R.id.sv_options)?.post {
                list.getChildAt(checked)?.let { row ->
                    view.findViewById<NestedScrollView>(R.id.sv_options)?.scrollTo(0, row.top)
                }
            }
        }
    }
}
