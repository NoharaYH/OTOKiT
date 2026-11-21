package com.otokit.noharayh.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.allViews
import com.otokit.noharayh.ui.animation.common.PressProgress
import com.otokit.noharayh.ui.constants.dimensions.LabeledFieldDimensions
import com.otokit.noharayh.ui.kit.common.PressSurface
import com.otokit.noharayh.ui.kit.settings.LabeledDropdownField
import com.otokit.noharayh.ui.kit.settings.LabeledFieldSurface
import com.otokit.noharayh.ui.kit.settings.SettingsTextField
import com.otokit.noharayh.ui.kit.settings.ThemeSelectorPanel
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class LabeledFieldTest : UiComponentTestHost() {
    @Test fun nativeEditingOwnsSelectionAndClickWhileTheSurfaceObservesOnePress() {
        lateinit var field: SettingsTextField
        main { field = SettingsTextField(activity, "输入", "abcdef") }
        mount(field, dp(260), -2)
        val input = field.allViews.filterIsInstance<EditText>().single()
        val surface = field.allViews.filterIsInstance<LabeledFieldSurface>().single()
        val presses = mutableListOf<Boolean>()
        var nativeClicks = 0
        var outerClicks = 0
        val values = mutableListOf<String>()
        main {
            surface.addOnPressStateChangedListener { presses += it }
            surface.setOnClickListener { outerClicks++ }
            input.setOnClickListener { nativeClicks++ }
            field.onValueChanged = { values += it }
            assertFalse(field.allViews.any { it is ThemeSelectorPanel || it is ImageView })
        }
        hardwareFrame(field).recycle()
        val textX = dp(LabeledFieldDimensions.TEXT_INSET_DP) + input.paint.measureText("abc") / 2f
        touch(surface, MotionEvent.ACTION_DOWN, Triple(0, textX, surface.height / 2f))
        touch(surface, MotionEvent.ACTION_UP, Triple(0, textX, surface.height / 2f))
        main {
            assertTrue(input.hasFocus())
            assertEquals(listOf(true, false), presses)
            assertEquals(0, outerClicks)
        }
        // 原生输入的首次点按先取得焦点；已聚焦后的点按才发普通 click。
        SystemClock.sleep(ViewConfiguration.getDoubleTapTimeout().toLong() + 16)
        touch(surface, MotionEvent.ACTION_DOWN, Triple(0, textX, surface.height / 2f))
        touch(surface, MotionEvent.ACTION_UP, Triple(0, textX, surface.height / 2f))
        diagnose(field, "native")
        main {
            assertTrue("native focus=${input.isFocusable}/${input.isFocusableInTouchMode} enabled=${input.isEnabled} " +
                "surface=${surface.isFocusable} window=${input.hasWindowFocus()} clicks=$nativeClicks/$outerClicks presses=$presses", input.hasFocus())
            assertEquals(listOf(true, false, true, false), presses)
            assertEquals(1, nativeClicks)
            assertEquals(0, outerClicks)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, surface.importantForAccessibility)
            assertTrue(input.performLongClick())
            assertTrue("Native long press must retain a text selection", input.selectionEnd > input.selectionStart)
            val editor = EditorInfo()
            val connection = requireNotNull(input.onCreateInputConnection(editor))
            assertEquals(EditorInfo.IME_ACTION_NEXT, editor.imeOptions and EditorInfo.IME_MASK_ACTION)
            input.setSelection(1, 4)
            assertTrue(connection.commitText("X", 1))
            assertEquals("aXef", field.text)
            assertEquals(listOf("aXef"), values)
            field.setText("saved")
            assertEquals("saved", field.text)
            assertEquals(listOf("aXef"), values)
            field.setInputEnabled(false)
            assertFalse(input.isEnabled || input.hasFocus())
            assertFalse(surface.isEnabled)
            val password = SettingsTextField(activity, "密码", "fixture",
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
            assertTrue(password.allViews.filterIsInstance<EditText>().single().transformationMethod is PasswordTransformationMethod)
        }
    }

    @Test fun longLabelsAndLargeTextMeasureNaturallyInANarrowField() {
        val configuration = android.content.res.Configuration(activity.resources.configuration).apply { fontScale = 3f }
        val context = activity.createConfigurationContext(configuration)
        val label = "这是一个需要在窄窗口正确省略且保留浮动缺口的较长输入标签"
        lateinit var field: SettingsTextField
        main { field = SettingsTextField(context, label, "A long native value"); field.showError("错误说明也应自然换行而不是覆盖编辑区。") }
        mount(field, dp(180), -2)
        diagnose(field, "large")
        main {
            val surface = field.allViews.filterIsInstance<LabeledFieldSurface>().single()
            val input = field.allViews.filterIsInstance<EditText>().single()
            val floating = surface.allViews.filterIsInstance<TextView>().single { it.text.toString() == label }
            assertTrue(floating.layout.getEllipsisCount(0) > 0)
            assertTrue(floating.width <= surface.width - 2 * dp(LabeledFieldDimensions.TEXT_INSET_DP))
            val labelTop = surface.top + floating.top + floating.translationY
            assertTrue(labelTop >= 0f)
            assertTrue("native text=${input.layout.height} in height=${input.height}",
                input.height >= input.layout.height + input.compoundPaddingTop + input.compoundPaddingBottom)
            val labelPosition = IntArray(2).also(floating::getLocationOnScreen)
            val valuePosition = IntArray(2).also(input::getLocationOnScreen)
            assertTrue("The floating label must not overlap the native text area",
                labelPosition[1] + floating.height <= valuePosition[1] + input.totalPaddingTop + 1)
            assertTrue(field.height > surface.height)
        }
    }

    @Test fun collapsedDropdownAndEditorUseTheSameOutlineAndDisabledPaletteOnHardware() {
        lateinit var field: SettingsTextField
        lateinit var dropdown: LabeledDropdownField
        lateinit var column: LinearLayout
        main {
            field = SettingsTextField(activity, "标签", "value")
            dropdown = LabeledDropdownField(activity, "标签").apply { setValue("value") }
            column = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                clipChildren = false; clipToPadding = false
                addView(field, LinearLayout.LayoutParams(-1, -2))
                addView(dropdown, LinearLayout.LayoutParams(-1, dp(LabeledFieldDimensions.HEIGHT_DP)).apply { topMargin = dp(30) })
            }
        }
        mount(column, dp(260), -2)
        val surface = field.allViews.filterIsInstance<LabeledFieldSurface>().single()
        for (disabled in listOf(false, true)) {
            val first = IntArray(2)
            val second = IntArray(2)
            main {
                field.setInputEnabled(!disabled)
                dropdown.setDisabledProgress(if (disabled) 1f else 0f)
                assertEquals(dropdown.height, surface.height)
                surface.getLocationOnScreen(first)
                dropdown.getLocationOnScreen(second)
            }
            val bitmap = hardwareFrame(column)
            try {
                val edge = (LabeledFieldDimensions.STROKE_DP * activity.resources.displayMetrics.density / 2f).roundToInt()
                val probes = listOf(surface.width / 2 to edge, edge to surface.height / 2,
                    surface.width - edge - 1 to surface.height / 2)
                for ((x, y) in probes) assertEquals(bitmap.getPixel(first[0] + x, first[1] + y),
                    bitmap.getPixel(second[0] + x, second[1] + y))
            } finally { bitmap.recycle() }
        }
    }

    @Test fun floatingLabelAndHorizontalPressHaloSurviveHalfAlphaOnHardware() {
        lateinit var field: SettingsTextField
        lateinit var fixture: FrameLayout
        main {
            field = SettingsTextField(activity, "浮动标签", "value")
            fixture = FrameLayout(activity).apply {
                clipChildren = false; clipToPadding = false
                val overflow = dp(LabeledFieldDimensions.FEEDBACK_RADIUS_DP)
                setPadding(overflow, overflow, overflow, overflow)
                addView(field, FrameLayout.LayoutParams(-1, -2))
            }
        }
        mount(fixture, dp(260 + 2 * LabeledFieldDimensions.FEEDBACK_RADIUS_DP), -2)
        val surface = field.allViews.filterIsInstance<LabeledFieldSurface>().single()
        val label = surface.allViews.filterIsInstance<TextView>().single { it.text.toString() == "浮动标签" }
        val body = IntArray(2)
        val floating = IntArray(2)
        main { surface.getLocationOnScreen(body); label.getLocationOnScreen(floating) }
        fun labelDarkness(bitmap: Bitmap): Int {
            var darkness = 0
            for (y in floating[1] until body[1]) for (x in floating[0] until floating[0] + label.width) {
                darkness = maxOf(darkness, 255 - Color.red(bitmap.getPixel(x, y)))
            }
            return darkness
        }
        val opaque = hardwareFrame(field) { labelDarkness(it) > 40 }
        val original = try { save(opaque, "opaque"); labelDarkness(opaque) } finally { opaque.recycle() }
        main { field.alpha = .5f }
        val half = hardwareFrame(field) { labelDarkness(it) in 16 until original }
        try {
            val faded = labelDarkness(half)
            save(half, "half")
            assertTrue("The floating label must remain visible above the surface", original > 40 && faded > 15)
            assertTrue("The label must participate in the same alpha composition", faded < original)
        } finally { half.recycle() }
        val progress = PressSurface::class.java.getDeclaredField("press").apply { isAccessible = true }.get(surface) as PressProgress
        try {
            touch(surface, MotionEvent.ACTION_DOWN, Triple(0, surface.width / 2f, surface.height / 2f))
            awaitMain { progress.value > .8f }
            val pressed = hardwareFrame(field)
            try {
                save(pressed, "pressed")
                val y = body[1] + surface.height / 2
                assertTrue("Left halo must extend beyond the field", Color.red(pressed.getPixel(body[0] - dp(1), y)) < 250)
                assertTrue("Right halo must extend beyond the field", Color.red(pressed.getPixel(body[0] + surface.width + dp(1), y)) < 250)
            } finally { pressed.recycle() }
        } finally { touch(surface, MotionEvent.ACTION_CANCEL, Triple(0, surface.width / 2f, surface.height / 2f)) }
    }

    private fun hardwareFrame(view: View, ready: (Bitmap) -> Boolean = { true }): Bitmap {
        val end = SystemClock.uptimeMillis() + 2000
        while (true) {
            repeat(2) {
                val submitted = CountDownLatch(1)
                main {
                    assertTrue(view.isHardwareAccelerated)
                    view.viewTreeObserver.registerFrameCommitCallback { submitted.countDown() }
                    view.invalidate()
                }
                assertTrue(submitted.await(2, TimeUnit.SECONDS))
            }
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            if (ready(bitmap) || SystemClock.uptimeMillis() >= end) return bitmap
            bitmap.recycle()
        }
    }

    private fun diagnose(view: View, name: String) {
        val bitmap = hardwareFrame(view)
        try { save(bitmap, name) } finally { bitmap.recycle() }
    }

    private fun save(bitmap: Bitmap, name: String) {
        File(activity.cacheDir, "labeled-$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private fun awaitMain(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 3000
        do {
            var ready = false
            main { ready = condition() }
            if (ready) return
            SystemClock.sleep(16)
        } while (SystemClock.uptimeMillis() < end)
        fail("The input state did not arrive")
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).roundToInt()
}
