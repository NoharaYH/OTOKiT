package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import android.graphics.Canvas
import com.otokit.noharayh.ui.animation.settings.SettingsPressHalo
import com.otokit.noharayh.ui.kit.common.PressSurface

/** 内容按钮的设置外观：只增加底层装饰，交互仍由 PressSurface 唯一处理。 */
open class SettingsPressSurface(context: Context) : PressSurface(context) {
    private val halo = SettingsPressHalo(this)

    override fun onPressStateChanged(pressed: Boolean) {
        if (pressed) halo.press() else halo.release()
    }

    override fun drawUnderlay(canvas: Canvas, cornerRadius: Float, inset: Float) {
        halo.draw(canvas, width, height, cornerRadius + inset)
    }

    override fun onDetachedFromWindow() {
        halo.reset()
        super.onDetachedFromWindow()
    }
}
