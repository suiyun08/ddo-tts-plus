package io.legado.app.lib.prefs

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.preference.PreferenceViewHolder
import io.legado.app.R
import io.legado.app.utils.dpToPx
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.utils.ColorUtils
import splitties.views.onLongClick
import kotlin.math.roundToInt

open class Preference(context: Context, attrs: AttributeSet) :
    androidx.preference.Preference(context, attrs) {

    private var onLongClick: ((preference: Preference) -> Boolean)? = null
    private val isBottomBackground: Boolean

    init {
        layoutResource = R.layout.view_preference
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.Preference)
        isBottomBackground = typedArray.getBoolean(R.styleable.Preference_isBottomBackground, false)
        typedArray.recycle()
    }

    companion object {

        /** 设置项图标底色 */
        private val iconColors = intArrayOf(
            0xFF4C8BF5.toInt(), 0xFF34A853.toInt(), 0xFFF29D38.toInt(), 0xFF8E6CEF.toInt(),
            0xFFEF6C8E.toInt(), 0xFF3DB7C4.toInt(), 0xFFE8A33D.toInt(), 0xFF7A8590.toInt()
        )

        fun <T : View> bindView(
            context: Context,
            viewHolder: PreferenceViewHolder?,
            icon: Drawable?,
            title: CharSequence?,
            summary: CharSequence?,
            weightLayoutRes: Int? = null,
            viewId: Int? = null,
            weightWidth: Int = 0,
            weightHeight: Int = 0,
            isBottomBackground: Boolean = false
        ): T? {
            if (viewHolder == null) return null
            val tvTitle = viewHolder.findViewById(R.id.preference_title) as? TextView
            tvTitle?.let {
                tvTitle.text = title
                tvTitle.isVisible = !title.isNullOrEmpty()
            }
            val tvSummary = viewHolder.findViewById(R.id.preference_desc) as? TextView
            tvSummary?.let {
                tvSummary.text = summary
                tvSummary.isGone = summary.isNullOrEmpty()
            }
            if (isBottomBackground && !viewHolder.itemView.isInEditMode) {
                val isLight = ColorUtils.isColorLight(context.bottomBackground)
                val pTextColor = context.getPrimaryTextColor(isLight)
                tvTitle?.setTextColor(pTextColor)
                val sTextColor = context.getSecondaryTextColor(isLight)
                tvSummary?.setTextColor(sTextColor)
            }
            val iconView = viewHolder.findViewById(R.id.preference_icon)
            if (iconView is ImageView) {
                iconView.isVisible = icon != null
                iconView.setImageDrawable(icon)
                if (icon != null && !AppConfig.isEInkMode) {
                    // 彩色圆角方块 + 白色图标，按标题固定取色，便于快速辨认
                    val size = 30.dpToPx()
                    iconView.layoutParams = iconView.layoutParams.apply {
                        width = size
                        height = size
                    }
                    iconView.setPadding(6.dpToPx(), 6.dpToPx(), 6.dpToPx(), 6.dpToPx())
                    iconView.background = GradientDrawable().apply {
                        cornerRadius = 8.dpToPx().toFloat()
                        setColor(iconColors[(title.toString().hashCode() % iconColors.size + iconColors.size) % iconColors.size])
                    }
                    iconView.setColorFilter(Color.WHITE)
                } else {
                    iconView.setColorFilter(context.accentColor)
                }
            }

            if (weightLayoutRes != null && weightLayoutRes != 0 && viewId != null && viewId != 0) {
                val lay = viewHolder.findViewById(R.id.preference_widget)
                if (lay is FrameLayout) {
                    var needRequestLayout = false
                    var v = viewHolder.itemView.findViewById<T>(viewId)
                    if (v == null) {
                        val inflater: LayoutInflater = LayoutInflater.from(context)
                        val childView = inflater.inflate(weightLayoutRes, null)
                        lay.removeAllViews()
                        lay.addView(childView)
                        lay.isVisible = true
                        v = lay.findViewById(viewId)
                    } else
                        needRequestLayout = true

                    if (weightWidth > 0 || weightHeight > 0) {
                        val lp = lay.layoutParams
                        if (weightHeight > 0)
                            lp.height =
                                (context.resources.displayMetrics.density * weightHeight).roundToInt()
                        if (weightWidth > 0)
                            lp.width =
                                (context.resources.displayMetrics.density * weightWidth).roundToInt()
                        lay.layoutParams = lp
                    } else if (needRequestLayout)
                        v.requestLayout()

                    return v
                }
            }

            return null
        }

    }

    final override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        onBindView(holder)
        onLongClick?.let { listener ->
            holder.itemView.onLongClick {
                listener.invoke(this)
            }
        }
    }

    open fun onBindView(holder: PreferenceViewHolder) {
        bindView<View>(
            context, holder, icon, title, summary,
            isBottomBackground = isBottomBackground
        )
    }

    fun onLongClick(listener: (preference: Preference) -> Boolean) {
        onLongClick = listener
    }

}
