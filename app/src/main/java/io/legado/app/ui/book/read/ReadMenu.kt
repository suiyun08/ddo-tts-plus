package io.legado.app.ui.book.read

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.Animation
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.core.view.isVisible
import io.legado.app.R
import io.legado.app.databinding.ViewReadMenuBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.model.ReadBook
import io.legado.app.ui.widget.seekbar.SeekBarChangeListener
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.activity
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.dpToPx
import io.legado.app.utils.invisible
import io.legado.app.utils.loadAnimation
import io.legado.app.utils.visible

/**
 * 阅读界面菜单：顶部工具栏 + 底部菜单栏（进度、自动翻页、目录/朗读/界面/设置/深色）。
 * 朗读、界面、设置的二级面板由阅读页贴在底部菜单栏上方显示，菜单栏保持可见，对应按钮高亮
 */
class ReadMenu @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    companion object {
        const val PANEL_ALOUD = "aloud"
        const val PANEL_STYLE = "style"
        const val PANEL_SETTING = "setting"
    }

    var canShowMenu: Boolean = false
    private val callBack: CallBack get() = activity as CallBack
    private val binding = ViewReadMenuBinding.inflate(LayoutInflater.from(context), this, true)
    private var confirmSkipToChapter: Boolean = false
    private var isMenuOutAnimating = false
    /** 当前展开的二级面板 */
    private var selectedPanel: String? = null
    private val menuTopIn: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_top_in)
    }
    private val menuTopOut: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_top_out)
    }
    private val menuBottomIn: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_bottom_in)
    }
    private val menuBottomOut: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_bottom_out)
    }
    private val immersiveMenu: Boolean
        get() = AppConfig.readBarStyleFollowPage && ReadBookConfig.durConfig.curBgType() == 0
    private var bgColor: Int = if (immersiveMenu) {
        kotlin.runCatching {
            Color.parseColor(ReadBookConfig.durConfig.curBgStr())
        }.getOrDefault(context.bottomBackground)
    } else {
        context.bottomBackground
    }
    private var textColor: Int = if (immersiveMenu) {
        ReadBookConfig.durConfig.curTextColor()
    } else {
        context.getPrimaryTextColor(ColorUtils.isColorLight(bgColor))
    }

    private var onMenuOutEnd: (() -> Unit)? = null
    private val menuInListener = object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation) {
            callBack.upSystemUiVisibility()
        }

        override fun onAnimationEnd(animation: Animation) {
            binding.vwMenuBg.setOnClickListener { callBack.onMenuBlankClick() }
            callBack.upSystemUiVisibility()
            if (!LocalConfig.readMenuHelpVersionIsLast) {
                callBack.showHelp()
            }
        }

        override fun onAnimationRepeat(animation: Animation) = Unit
    }
    private val menuOutListener = object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation) {
            isMenuOutAnimating = true
            binding.vwMenuBg.setOnClickListener(null)
        }

        override fun onAnimationEnd(animation: Animation) {
            this@ReadMenu.invisible()
            binding.titleBar.invisible()
            binding.bottomMenu.invisible()
            canShowMenu = false
            isMenuOutAnimating = false
            onMenuOutEnd?.invoke()
            callBack.upSystemUiVisibility()
        }

        override fun onAnimationRepeat(animation: Animation) = Unit
    }

    init {
        initView()
        upBrightnessState()
        bindEvent()
    }

    private fun initView(reset: Boolean = false) = binding.run {
        initAnimation()
        if (immersiveMenu) {
            titleBar.setTextColor(textColor)
            titleBar.setBackgroundColor(bgColor)
            titleBar.setColorFilter(textColor)
        } else if (reset) {
            val bgColor = context.primaryColor
            val textColor = context.primaryTextColor
            titleBar.setTextColor(textColor)
            titleBar.setBackgroundColor(bgColor)
            titleBar.setColorFilter(textColor)
        }
        if (AppConfig.isEInkMode) {
            titleBar.setBackgroundResource(R.drawable.bg_eink_border_bottom)
            llBottomBg.setBackgroundResource(R.drawable.bg_eink_border_top)
        } else {
            llBottomBg.setBackgroundColor(bgColor)
        }
        vwBottomDivider.setBackgroundColor(ColorUtils.withAlpha(textColor, 0.12f))
        ivAutoPage.background = Selector.shapeBuild()
            .setCornerRadius(18.dpToPx())
            .setDefaultBgColor(ColorUtils.withAlpha(textColor, 0.08f))
            .setPressedBgColor(ColorUtils.withAlpha(textColor, 0.16f))
            .create()
        upNightView()
        upTabColors()
        /**
         * 确保视图不被导航栏遮挡
         */
        applyNavigationBarPadding()
    }

    fun reset() {
        upColorConfig()
        initView(true)
    }

    fun refreshMenuColorFilter() {
        if (immersiveMenu) {
            binding.titleBar.setColorFilter(textColor)
        }
    }

    private fun upColorConfig() {
        bgColor = if (immersiveMenu) {
            kotlin.runCatching {
                Color.parseColor(ReadBookConfig.durConfig.curBgStr())
            }.getOrDefault(context.bottomBackground)
        } else {
            context.bottomBackground
        }
        textColor = if (immersiveMenu) {
            ReadBookConfig.durConfig.curTextColor()
        } else {
            context.getPrimaryTextColor(ColorUtils.isColorLight(bgColor))
        }
    }

    /** 按当前设置应用屏幕亮度（亮度调节在「界面」面板里） */
    fun upBrightnessState() {
        activity?.let { ReadBrightness.apply(it) }
    }

    fun runMenuIn(anim: Boolean = !AppConfig.isEInkMode) {
        callBack.onMenuShow()
        this.visible()
        binding.titleBar.visible()
        binding.bottomMenu.visible()
        if (anim) {
            binding.titleBar.startAnimation(menuTopIn)
            binding.bottomMenu.startAnimation(menuBottomIn)
        } else {
            menuInListener.onAnimationStart(menuBottomIn)
            menuInListener.onAnimationEnd(menuBottomIn)
        }
    }

    fun runMenuOut(anim: Boolean = !AppConfig.isEInkMode, onMenuOutEnd: (() -> Unit)? = null) {
        if (isMenuOutAnimating) {
            return
        }
        callBack.dismissMenuPanel()
        callBack.onMenuHide()
        this.onMenuOutEnd = onMenuOutEnd
        if (this.isVisible) {
            if (anim) {
                binding.titleBar.startAnimation(menuTopOut)
                binding.bottomMenu.startAnimation(menuBottomOut)
            } else {
                menuOutListener.onAnimationStart(menuBottomOut)
                menuOutListener.onAnimationEnd(menuBottomOut)
            }
        }
    }

    /** 底部菜单栏顶边在屏幕上的位置，二级面板贴在它上方；菜单未显示时返回 null */
    fun bottomMenuTopOnScreen(): Int? {
        if (!isVisible || !binding.bottomMenu.isVisible || binding.bottomMenu.height == 0) {
            return null
        }
        val location = IntArray(2)
        binding.bottomMenu.getLocationOnScreen(location)
        return location[1]
    }

    /** 标出当前展开的二级面板对应的按钮，null 表示没有展开的面板 */
    fun setSelectedPanel(panel: String?) {
        selectedPanel = panel
        upTabColors()
    }

    fun clearSelectedPanel(panel: String) {
        if (selectedPanel == panel) setSelectedPanel(null)
    }

    private fun upTabColors() = binding.run {
        val accent = context.accentColor
        val tabs = listOf(
            Triple(null, ivCatalog, tvCatalog),
            Triple(PANEL_ALOUD, ivReadAloud, tvReadAloud),
            Triple(PANEL_STYLE, ivFont, tvFont),
            Triple(PANEL_SETTING, ivSetting, tvSetting),
            Triple(null, ivNight, tvNight),
        )
        tabs.forEach { (panel, icon, text) ->
            val selected = panel != null && panel == selectedPanel
            val color = if (selected) accent else textColor
            icon.setColorFilter(color, PorterDuff.Mode.SRC_IN)
            text.setTextColor(color)
            icon.background = if (selected) {
                Selector.shapeBuild()
                    .setCornerRadius(14.dpToPx())
                    .setDefaultBgColor(ColorUtils.withAlpha(accent, 0.14f))
                    .create()
            } else null
        }
        ivAutoPage.setColorFilter(textColor, PorterDuff.Mode.SRC_IN)
    }

    private fun upNightView() = binding.run {
        if (AppConfig.isNightTheme) {
            ivNight.setImageResource(R.drawable.ic_daytime)
            tvNight.setText(R.string.read_menu_light)
        } else {
            ivNight.setImageResource(R.drawable.ic_brightness)
            tvNight.setText(R.string.read_menu_dark)
        }
    }

    private fun bindEvent() = binding.run {
        vwMenuBg.setOnClickListener { callBack.onMenuBlankClick() }
        titleBar.toolbar.setOnClickListener {
            callBack.openBookInfoActivity()
        }
        //阅读进度
        seekReadPage.setOnSeekBarChangeListener(object : SeekBarChangeListener {

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                binding.vwMenuBg.setOnClickListener(null)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                binding.vwMenuBg.setOnClickListener { callBack.onMenuBlankClick() }
                when (AppConfig.progressBarBehavior) {
                    "page" -> ReadBook.skipToPage(seekBar.progress)
                    "chapter" -> {
                        if (confirmSkipToChapter) {
                            callBack.skipToChapter(seekBar.progress)
                        } else {
                            context.alert("章节跳转确认", "确定要跳转章节吗？") {
                                yesButton {
                                    confirmSkipToChapter = true
                                    callBack.skipToChapter(seekBar.progress)
                                }
                                noButton {
                                    upSeekBar()
                                }
                                onCancelled {
                                    upSeekBar()
                                }
                            }
                        }
                    }
                }
            }

        })

        //自动翻页
        ivAutoPage.setOnClickListener {
            runMenuOut {
                callBack.autoPage()
            }
        }

        //目录
        llCatalog.setOnClickListener {
            runMenuOut {
                callBack.openChapterList()
            }
        }

        //朗读：未在朗读时开始朗读，并展开朗读面板
        llReadAloud.setOnClickListener {
            callBack.toggleMenuPanel(PANEL_ALOUD)
        }
        //界面
        llFont.setOnClickListener {
            callBack.toggleMenuPanel(PANEL_STYLE)
        }
        //设置
        llSetting.setOnClickListener {
            callBack.toggleMenuPanel(PANEL_SETTING)
        }
        //深色/浅色
        llNight.setOnClickListener {
            // 切换会重建页面，先收起面板，避免重建后面板脱离菜单栏
            callBack.dismissMenuPanel()
            AppConfig.isNightTheme = !AppConfig.isNightTheme
            ThemeConfig.applyDayNight(context)
        }
    }

    private fun initAnimation() {
        menuTopIn.setAnimationListener(menuInListener)
        menuTopOut.setAnimationListener(menuOutListener)
    }

    fun upBookView() {
        binding.titleBar.title = ReadBook.book?.name
        ReadBook.curTextChapter?.let {
            upSeekBar()
        }
    }

    fun upSeekBar() {
        binding.seekReadPage.apply {
            when (AppConfig.progressBarBehavior) {
                "page" -> {
                    ReadBook.curTextChapter?.let {
                        max = it.pageSize.minus(1)
                        progress = ReadBook.durPageIndex
                    }
                }

                "chapter" -> {
                    max = ReadBook.simulatedChapterSize - 1
                    progress = ReadBook.durChapterIndex
                }
            }
        }
    }

    fun setSeekPage(seek: Int) {
        binding.seekReadPage.progress = seek
    }

    fun setAutoPage(autoPage: Boolean) = binding.run {
        if (autoPage) {
            ivAutoPage.setImageResource(R.drawable.ic_auto_page_stop)
            ivAutoPage.contentDescription = context.getString(R.string.auto_next_page_stop)
        } else {
            ivAutoPage.setImageResource(R.drawable.ic_auto_page)
            ivAutoPage.contentDescription = context.getString(R.string.auto_next_page)
        }
        ivAutoPage.setColorFilter(textColor, PorterDuff.Mode.SRC_IN)
    }

    interface CallBack {
        fun autoPage()
        fun openReplaceRule()
        fun openChapterList()
        fun openSearchActivity(searchWord: String?)
        fun openSourceEditActivity()
        fun openBookInfoActivity()
        fun showReadStyle()
        fun showMoreSetting()
        fun showReadAloudDialog()
        fun upSystemUiVisibility()
        fun onClickReadAloud()
        fun showHelp()
        fun showLogin()
        fun payAction()
        fun disableSource()
        fun skipToChapter(index: Int)
        fun onMenuShow()
        fun onMenuHide()

        /** 展开或收起底部菜单的二级面板（朗读、界面、设置） */
        fun toggleMenuPanel(panel: String)

        /** 收起当前展开的二级面板 */
        fun dismissMenuPanel()

        /** 点击菜单外的空白处：有展开的面板先收起面板，否则收起菜单 */
        fun onMenuBlankClick()
    }

}
