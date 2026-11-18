package com.otokit.noharayh.ui.pages

import android.content.Context
import android.widget.Toast
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.animation.common.GameFrame
import com.otokit.noharayh.ui.kit.common.GamePlaceholderPanel
import com.otokit.noharayh.ui.kit.common.PageContent
import kotlin.math.roundToInt

/**
 * 三个功能共用的六占位页面骨架。
 *
 * 页面自己持有 Mai/Chu 两个测试占位根；shell 只传 [GameFrame]，
 * 不取得内部 View，也不在这里提交导航或音游状态。
 */
object GamePlaceholderPage {

    fun create(
        context: Context,
        maiDescription: CharSequence,
        chuDescription: CharSequence,
        buttonLabel: String? = null,
        buttonFillColor: Int? = null,
        onButtonClick: (() -> Unit)? = null,
    ): PageContent {
        val toast = Toast.makeText(context, R.string.press_surface_preview_clicked, Toast.LENGTH_SHORT)

        val maiPanel = GamePlaceholderPanel(
            context = context,
            description = maiDescription,
            buttonDescription = buttonLabel ?: context.getString(
                R.string.placeholder_button_description,
                maiDescription,
            ),
            onButtonClick = onButtonClick ?: { toast.show() },
            buttonLabel = buttonLabel,
        )
        val chuPanel = GamePlaceholderPanel(
            context = context,
            description = chuDescription,
            buttonDescription = buttonLabel ?: context.getString(
                R.string.placeholder_button_description,
                chuDescription,
            ),
            onButtonClick = onButtonClick ?: { toast.show() },
            buttonLabel = buttonLabel,
        )
        val root = GamePairLayout(maiPanel, chuPanel) { mai, chu ->
            maiPanel.setInteractive(mai)
            chuPanel.setInteractive(chu)
        }
        return PageContent(
            view = root,
            gameColorsUpdater = { maiColor, chuColor ->
                maiPanel.button.fillColor = buttonFillColor ?: maiColor
                chuPanel.button.fillColor = buttonFillColor ?: chuColor
            },
            frameUpdater = { frame ->
                maiPanel.setWindowWidth(frame.windowWidthPx)
                chuPanel.setWindowWidth(frame.windowWidthPx)
                root.render(frame)
            },
            interactiveUpdater = root::setInteractive,
        )
    }

    /** 测试用纯计算：按钮高度按 160dp 的三分之一取整。 */
    fun testButtonHeightPx(density: Float): Int = ((160f / 3f) * density).roundToInt()
}
