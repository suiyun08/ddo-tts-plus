package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.SeekBar
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogAutoReadBinding
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.BaseReadBookActivity
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadMenu
import io.legado.app.ui.widget.seekbar.SeekBarChangeListener
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.viewbindingdelegate.viewBinding
import java.util.Locale


class AutoReadDialog : BaseDialogFragment(R.layout.dialog_auto_read) {

    private val binding by viewBinding(DialogAutoReadBinding::bind)
    private val callBack: CallBack? get() = activity as? CallBack

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(R.color.background)
            decorView.setPadding(0, 0, 0, 0)
            val attr = attributes
            attr.dimAmount = 0.0f
            attr.gravity = Gravity.BOTTOM
            attributes = attr
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        placeAboveReadMenu()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as? ReadBookActivity)?.onMenuPanelDismiss(ReadMenu.PANEL_AUTO)
        (activity as ReadBookActivity).bottomDialog--
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = binding.run {
        val bottomDialog = (activity as ReadBookActivity).bottomDialog++
        if (bottomDialog > 0) {
            dismiss()
            return@run
        }
        val bg = requireContext().bottomBackground
        val textColor = requireContext().menuTextColor
        root.setBackgroundColor(bg)
        tvAutoTitle.setTextColor(textColor)
        tvReadSpeedTitle.setTextColor(textColor)
        tvReadSpeed.setTextColor(textColor)
        listOf(tvPageAnim, tvAutoPageStop).forEach {
            it.background = MenuPanelStyle.tile(textColor)
            it.setTextColor(textColor)
        }
        initOnChange()
        initData()
        initEvent()
    }

    private fun initData() {
        val speed = if (ReadBookConfig.autoReadSpeed < 1) 1 else ReadBookConfig.autoReadSpeed
        binding.tvReadSpeed.text = String.format(Locale.ROOT, "%d 秒/页", speed)
        binding.seekAutoRead.progress = speed
    }

    private fun initOnChange() {
        binding.seekAutoRead.setOnSeekBarChangeListener(object : SeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val speed = if (progress < 1) 1 else progress
                binding.tvReadSpeed.text = String.format(Locale.ROOT, "%d 秒/页", speed)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                ReadBookConfig.autoReadSpeed =
                    if (binding.seekAutoRead.progress < 1) 1 else binding.seekAutoRead.progress
                upTtsSpeechRate()
            }
        })
    }

    private fun initEvent() {
        binding.tvPageAnim.setOnClickListener {
            (activity as BaseReadBookActivity).showPageAnimConfig {
                (activity as ReadBookActivity).upPageAnim()
                ReadBook.loadContent(false)
            }
        }
        binding.tvAutoPageStop.setOnClickListener {
            callBack?.autoPageStop()
            binding.tvAutoPageStop.post {
                dismissAllowingStateLoss()
            }
        }
    }

    private fun upTtsSpeechRate() {
        ReadAloud.upTtsSpeechRate(requireContext())
        if (!BaseReadAloudService.pause) {
            ReadAloud.pause(requireContext())
            ReadAloud.resume(requireContext())
        }
    }

    interface CallBack {
        fun showMenuBar()
        fun openChapterList()
        fun autoPageStop()
    }
}