package com.otokit.noharayh.ui.kit.common

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
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
    private val ascii = Regex("[\\x20-\\x7e]+")

    /** 只替换 ASCII 字段，保留中文字体与调用方已有的颜色、粗体等 span。 */
    fun withLogLatin(context: Context, text: CharSequence): CharSequence {
        val typeface = resolve(context, AppFonts.Role.LogLatin, bold = false) ?: return text
        return SpannableString(text).apply {
            for (match in ascii.findAll(text)) {
                setSpan(LogLatinSpan(typeface), match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    /** 正文与中文标题：江城圆体；bold 使用合成粗体。 */
    fun applyAppTypeface(textView: TextView, bold: Boolean = false) {
        apply(textView, AppFonts.Role.Body, bold)
    }

    /** 英文标题：ROG Sans UB；已是标题字形，不叠加合成粗体。 */
    fun applyTitleBarRogSans(textView: TextView) {
        apply(textView, AppFonts.Role.EnglishTitle, bold = false)
    }

    fun apply(textView: TextView, role: AppFonts.Role, bold: Boolean = false) {
        val effectiveBold = role == AppFonts.Role.Body && bold
        val typeface = resolve(textView.context, role, effectiveBold) ?: return
        // 字形已按角色准备好；清除控件此前残留的合成粗体和斜体，不再叠加样式。
        textView.setTypeface(typeface, Typeface.NORMAL)
    }

    private fun resolve(context: Context, role: AppFonts.Role, bold: Boolean): Typeface? {
        val key = if (bold) "${role.assetPath}#bold" else "${role.assetPath}#regular"
        synchronized(cache) { cache[key] }?.let { return it }

        val base = load(context, role.assetPath) ?: return null
        val styled = when {
            bold -> Typeface.create(base, Typeface.BOLD)
            else -> base
        }
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

    private class LogLatinSpan(private val typeface: Typeface) : MetricAffectingSpan() {
        override fun updateDrawState(paint: TextPaint) = updateMeasureState(paint)
        override fun updateMeasureState(paint: TextPaint) {
            paint.typeface = Typeface.create(typeface, paint.typeface?.style ?: Typeface.NORMAL)
        }
    }
}
