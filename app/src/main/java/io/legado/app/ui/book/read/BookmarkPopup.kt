package io.legado.app.ui.book.read

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.Bookmark
import io.legado.app.databinding.ItemBookmarkPopupBinding
import io.legado.app.databinding.PopupBookmarkViewBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.book.bookmark.BookmarkDialog
import io.legado.app.utils.dpToPx
import io.legado.app.utils.showDialogFragment
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/**
 * 阅读页点击书签文字后显示的书签浮窗
 * 点关闭按钮或浮窗外区域关闭，外部点击会被浮窗消费，不会触发翻页
 */
class BookmarkPopup(private val activity: AppCompatActivity) :
    PopupWindow(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {

    private val inflater = LayoutInflater.from(activity)
    private val binding = PopupBookmarkViewBinding.inflate(inflater)
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    init {
        contentView = binding.root
        isFocusable = true
        isOutsideTouchable = true
        setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        binding.ivClose.setOnClickListener { dismiss() }
    }

    /**
     * @param anchor 正文视图
     * @param x 点击位置横坐标（相对 anchor）
     * @param lineTop 被点击行的顶部（相对 anchor）
     * @param lineBottom 被点击行的底部（相对 anchor）
     */
    fun show(
        anchor: View,
        bookmarks: List<Bookmark>,
        x: Float,
        lineTop: Float,
        lineBottom: Float
    ) {
        if (bookmarks.isEmpty()) return
        binding.tvChapterName.text = bookmarks.first().chapterName
        binding.llItems.removeAllViews()
        bookmarks.forEachIndexed { index, bookmark ->
            val item = ItemBookmarkPopupBinding.inflate(inflater, binding.llItems, false)
            item.tvBookText.text = bookmark.bookText
            item.tvContent.text = bookmark.content
            item.tvContent.isVisible = bookmark.content.isNotBlank()
            item.tvTime.text = timeFormat.format(Date(bookmark.time))
            item.tvEdit.setTextColor(ThemeStore.accentColor)
            item.tvEdit.setOnClickListener {
                dismiss()
                activity.showDialogFragment(BookmarkDialog(bookmark, 0))
            }
            item.tvDelete.setOnClickListener {
                dismiss()
                activity.alert(R.string.sure_del) {
                    yesButton {
                        activity.lifecycleScope.launch(IO) {
                            appDb.bookmarkDao.delete(bookmark)
                        }
                    }
                    noButton()
                }
            }
            item.vDivider.isVisible = index < bookmarks.lastIndex
            binding.llItems.addView(item.root)
        }

        val margin = 16.dpToPx()
        val popupWidth = min(anchor.width - margin * 2, 360.dpToPx())
        binding.root.measure(
            View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupHeight = min(binding.root.measuredHeight, anchor.height * 2 / 5)
        width = popupWidth
        height = popupHeight

        val location = IntArray(2)
        anchor.getLocationInWindow(location)
        val left = (location[0] + x - popupWidth / 2).toInt()
            .coerceIn(location[0] + margin, location[0] + anchor.width - margin - popupWidth)
        val gap = 8.dpToPx()
        var top = (location[1] + lineTop).toInt() - popupHeight - gap
        if (top < location[1]) {
            // 上方放不下时显示在被点击行的下方
            top = (location[1] + lineBottom).toInt() + gap
        }
        binding.scrollView.scrollTo(0, 0)
        showAtLocation(anchor, Gravity.NO_GRAVITY, left, top)
    }

}
