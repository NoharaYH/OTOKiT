package com.otokit.noharayh.ui.kit.common

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import android.widget.TextView
import com.otokit.noharayh.ui.constants.typography.AppFonts

/**
 * 原生字体的唯一加载、缓存与应用入口，替代旧的 `ResourcesCompat.getFont` / `R.font` 方式。
 *
 * 只改变字体：不改字号、行高、颜色或 padding，也不遍历 View 树批量替换。
 * 缓存只保存 Typeface，不持有 Context、Activity、View 或任何宿主引用。
 */
object AppTypography {

    private const val TAG = "AppTypography"

    private val cache = HashMap<String, Typeface>()

    /** 正文与中文标题：江城圆体；bold 使用合成粗体。 */
    fun applyAppTypeface(textView: TextView, bold: Boolean = false) {
        apply(textView, AppFonts.Role.Body, bold)
    }

    /** 英文标题：ROG Sans UB；已是标题字形，不叠加合成粗体。 */
    fun applyTitleBarRogSans(textView: TextView) {
        apply(textView, AppFonts.Role.EnglishTitle, bold = false)
    }

    fun apply(textView: TextView, role: AppFonts.Role, bold: Boolean = false) {
        val typeface = resolve(textView.context, role, bold) ?: return
        textView.typeface = typeface
    }

    private fun resolve(context: Context, role: AppFonts.Role, bold: Boolean): Typeface? {
        val key = if (bold) "${role.assetPath}#bold" else "${role.assetPath}#regular"
        synchronized(cache) { cache[key] }?.let { return it }

        val base = load(context, role.assetPath) ?: return null
        val styled = if (bold) Typeface.create(base, Typeface.BOLD) else base
        synchronized(cache) { cache[key] = styled }
        return styled
    }

    private fun load(context: Context, path: String): Typeface? =
        try {
            Typeface.createFromAsset(context.applicationContext.assets, path)
        } catch (error: RuntimeException) {
            Log.w(TAG, "字体加载失败，保留调用方原字体：$path", error)
            null
        }
}
