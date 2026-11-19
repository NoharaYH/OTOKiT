package com.otokit.noharayh.ui.kit.pageScoreSync

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.kit.common.PressSurface
import com.otokit.noharayh.ui.kit.common.ViewPrimitives.dp

internal enum class MaiDifficulty(val label: String, val artwork: Int, val color: Int) {
    Basic("Basic", R.drawable.difficulty_mai_basic, 0xff45c124.toInt()),
    Advanced("Advanced", R.drawable.difficulty_mai_advanced, 0xffffba01.toInt()),
    Expert("Expert", R.drawable.difficulty_mai_expert, 0xffff5a66.toInt()),
    Master("Master", R.drawable.difficulty_mai_master, 0xff9f51dc.toInt()),
    ReMaster("Re:Master", R.drawable.difficulty_mai_remaster, 0xffe6e6e6.toInt()),
    Utage("U·TA·GE", R.drawable.difficulty_mai_utage, 0xffff6ffd.toInt()),
}

internal class MaiDifficultyButton(context: Context, private val difficulty: MaiDifficulty) : PressSurface(context) {
    private val artwork = ImageView(context)
    fun setArtworkHeight(height: Int) {
        val params = artwork.layoutParams
        if (params.height != height) {
            params.height = height
            artwork.layoutParams = params
        }
    }

    private val dimFill = DifficultySelectionColors.dim(difficulty.color)

    override fun setSelected(selected: Boolean) {
        if (selected == isSelected) return
        super.setSelected(selected)
        fillColor = if (selected) difficulty.color else dimFill
        artwork.colorFilter = if (selected) null else DifficultySelectionColors.dimFilter
    }

    init {
        baseRadius = dp(context, 12).toFloat()
        fillColor = dimFill
        outerHaloEnabled = true
        innerRippleEnabled = false
        contentDescription = difficulty.label
        val inset = dp(context, 4)
        addView(artwork.apply {
            setImageResource(difficulty.artwork)
            colorFilter = DifficultySelectionColors.dimFilter
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(-1, dp(context, 34), Gravity.CENTER).apply {
            setMargins(inset, inset, inset, inset)
        })
    }

}
