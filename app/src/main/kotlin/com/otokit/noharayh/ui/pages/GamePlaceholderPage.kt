package com.otokit.noharayh.ui.pages

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.animation.common.GameFrame
import com.otokit.noharayh.ui.animation.common.GamePhase
import com.otokit.noharayh.ui.animation.common.TransitionFrames
import com.otokit.noharayh.ui.constants.motion.PagerPairTransition
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
    ): PageContent {
        val root = FrameLayout(context)
        val toast = Toast.makeText(context, R.string.press_surface_preview_clicked, Toast.LENGTH_SHORT)

        val maiPanel = GamePlaceholderPanel(
            context = context,
            description = maiDescription,
            buttonDescription = context.getString(
                R.string.placeholder_button_description,
                maiDescription,
            ),
            onButtonClick = { toast.show() },
        )
        val chuPanel = GamePlaceholderPanel(
            context = context,
            description = chuDescription,
            buttonDescription = context.getString(
                R.string.placeholder_button_description,
                chuDescription,
            ),
            onButtonClick = { toast.show() },
        )
        root.addView(
            maiPanel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(
            chuPanel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        var interactiveAllowed = true
        var lastFrame: GameFrame? = null

        fun applyFrame(frame: GameFrame) {
            lastFrame = frame
            maiPanel.setWindowWidth(frame.windowWidthPx)
            chuPanel.setWindowWidth(frame.windowWidthPx)
            val progress = frame.normalizedProgress
            if (frame.phase == GamePhase.Idle) {
                val selectedMai = frame.selectedGame == com.otokit.noharayh.ui.theme.GameLine.Mai
                showEndpoint(maiPanel, selectedMai)
                showEndpoint(chuPanel, !selectedMai)
                maiPanel.setInteractive(interactiveAllowed && selectedMai)
                chuPanel.setInteractive(interactiveAllowed && !selectedMai)
                return
            }

            maiPanel.visibility = View.VISIBLE
            chuPanel.visibility = View.VISIBLE
            val slideDistance = root.width * PagerPairTransition.TRANSLATION_RATIO
            val (maiFrame, chuFrame) = TransitionFrames.pagerPair(
                kind = PagerPairTransition.KIND,
                progress = progress,
                slideDistancePx = slideDistance,
                minScale = PagerPairTransition.MIN_SCALE,
                minAlpha = PagerPairTransition.MIN_ALPHA,
            )
            applyPanelFrame(maiPanel, maiFrame)
            applyPanelFrame(chuPanel, chuFrame)
            maiPanel.setInteractive(false)
            chuPanel.setInteractive(false)
        }

        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            lastFrame?.let(::applyFrame)
        }

        return PageContent(
            view = root,
            frameUpdater = ::applyFrame,
            interactiveUpdater = { allowed ->
                interactiveAllowed = allowed
                lastFrame?.let(::applyFrame)
            },
            disposeAction = {
                // Toast/面板无运行对象；移除监听即可，root 由 shell 移除。
                lastFrame = null
            },
        )
    }

    private fun showEndpoint(panel: GamePlaceholderPanel, visible: Boolean) {
        panel.visibility = if (visible) View.VISIBLE else View.GONE
        panel.alpha = 1f
        panel.scaleX = 1f
        panel.scaleY = 1f
        panel.translationX = 0f
    }

    private fun applyPanelFrame(panel: GamePlaceholderPanel, frame: com.otokit.noharayh.ui.animation.common.TransitionFrame) {
        panel.alpha = frame.alpha
        panel.scaleX = frame.scale
        panel.scaleY = frame.scale
        panel.translationX = frame.translationX
        panel.translationY = frame.translationY
    }

    /** 测试用纯计算：按钮高度按 160dp 的三分之一取整。 */
    fun testButtonHeightPx(density: Float): Int = ((160f / 3f) * density).roundToInt()
}
