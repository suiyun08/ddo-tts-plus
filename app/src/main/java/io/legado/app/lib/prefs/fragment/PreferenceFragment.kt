package io.legado.app.lib.prefs.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.legado.app.lib.prefs.EditTextPreferenceDialog
import io.legado.app.lib.prefs.ListPreferenceDialog
import io.legado.app.lib.prefs.MultiSelectListPreferenceDialog
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.ui.book.read.config.GroupCardDecoration
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.dpToPx

abstract class PreferenceFragment : PreferenceFragmentCompat() {

    private val dialogFragmentTag = "androidx.preference.PreferenceFragment.DIALOG"

    /** 分组卡片和分隔线的基准文字颜色，阅读菜单里的设置面板改用菜单底色对应的文字色 */
    open val groupCardTextColor: Int
        get() = requireContext().getPrimaryTextColor(
            ColorUtils.isColorLight(requireContext().backgroundColor)
        )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.clipToPadding = false
        listView.applyNavigationBarPadding()
        // 每组设置画成一张圆角卡片，组内各项之间有细分隔线
        setDivider(null)
        listView.addItemDecoration(GroupCardDecoration(groupCardTextColor))
        listView.setPadding(
            listView.paddingLeft, listView.paddingTop,
            listView.paddingRight, listView.paddingBottom + 12.dpToPx()
        )
    }

    @SuppressLint("RestrictedApi")
    override fun onDisplayPreferenceDialog(preference: Preference) {

        var handled = false
        if (callbackFragment is OnPreferenceDisplayDialogCallback) {
            handled =
                (callbackFragment as OnPreferenceDisplayDialogCallback)
                    .onPreferenceDisplayDialog(this, preference)
        }
        if (!handled && activity is OnPreferenceDisplayDialogCallback) {
            handled = (activity as OnPreferenceDisplayDialogCallback)
                .onPreferenceDisplayDialog(this, preference)
        }

        if (handled) {
            return
        }

        // check if dialog is already showing
        if (parentFragmentManager.findFragmentByTag(dialogFragmentTag) != null) {
            return
        }

        val dialogFragment: DialogFragment = when (preference) {
            is EditTextPreference -> {
                EditTextPreferenceDialog.newInstance(preference.getKey())
            }
            is ListPreference -> {
                ListPreferenceDialog.newInstance(preference.getKey())
            }
            is MultiSelectListPreference -> {
                MultiSelectListPreferenceDialog.newInstance(preference.getKey())
            }
            else -> {
                throw IllegalArgumentException(
                    "Cannot display dialog for an unknown Preference type: "
                            + preference.javaClass.simpleName
                            + ". Make sure to implement onPreferenceDisplayDialog() to handle "
                            + "displaying a custom dialog for this Preference."
                )
            }
        }
        @Suppress("DEPRECATION")
        dialogFragment.setTargetFragment(this, 0)

        dialogFragment.show(parentFragmentManager, dialogFragmentTag)
    }

}