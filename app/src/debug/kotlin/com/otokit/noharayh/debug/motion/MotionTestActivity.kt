package com.otokit.noharayh.debug.motion

import com.otokit.noharayh.ui.shell.navigation.Destination
import android.widget.AdapterView
import com.otokit.noharayh.ui.window.PixelRect
import com.otokit.noharayh.ui.chrome.SideBarHost
import com.otokit.noharayh.ui.animation.common.resistedDragDistance
import com.otokit.noharayh.ui.constants.dimensions.GameSwipeDimensions
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.otokit.noharayh.ui.kit.common.ViewPrimitives
import com.otokit.noharayh.ui.kit.settings.SettingsPageScrollView
import kotlin.math.abs

/** Standalone ADB preview. All records and navigation state live inside this activity. */
class MotionTestActivity : ComponentActivity() {
    private val paper = Color.rgb(249, 248, 251)
    private val ink = Color.rgb(36, 35, 43)
    private var game = 0
    private var feature = 0
    private var level = 0
    private val scrollPositions = IntArray(4)
    private lateinit var picker: Spinner
    private lateinit var systemScroll: CheckBox
    private lateinit var stage: FrameLayout
    private lateinit var actions: LinearLayout
    private lateinit var page: View
    private var transition: ValueAnimator? = null
    private val motion get() = PreviewMotion.entries[picker.selectedItemPosition]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = column().apply { setBackgroundColor(paper) }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(4), dp(12), dp(4))
        }
        val drawer = SideBarHost(this)
        controls.addView(button("侧边栏") { drawer.open() })
        picker = Spinner(this).apply {
            adapter = ArrayAdapter(this@MotionTestActivity, android.R.layout.simple_spinner_dropdown_item,
                PreviewMotion.entries.map { it.label })
            setSelection(PreviewMotion.SMOOTH.ordinal)
            contentDescription = "选择翻页动效"
        }
        controls.addView(picker, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(controls)
        systemScroll = CheckBox(this).apply {
            text = "对比系统原生滚动"
            isChecked = savedInstanceState?.getBoolean("native") ?: false
            setOnCheckedChangeListener { _, _ ->
                if (level >= 2) {
                    transition?.end()
                    rememberScroll()
                    stage.removeAllViews()
                    page = makePage()
                    stage.addView(page)
                }
            }
        }
        root.addView(systemScroll)
        stage = FrameLayout(this)
        root.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f))
        actions = LinearLayout(this).apply { setPadding(dp(8), dp(4), dp(8), dp(4)) }
        root.addView(actions)
        game = savedInstanceState?.getInt("game") ?: 0
        feature = savedInstanceState?.getInt("feature") ?: 0
        level = savedInstanceState?.getInt("level") ?: 0
        savedInstanceState?.getIntArray("scroll")?.copyInto(scrollPositions)
        savedInstanceState?.let { picker.setSelection(it.getInt("motion")) }
        page = makePage()
        stage.addView(page)
        updateActions()
        drawer.addView(root, 0)
        drawer.setDragEnabled(true)
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            drawer.setMetrics(PixelRect(root.paddingLeft, root.paddingTop, root.width - root.paddingRight,
                root.height - root.paddingBottom), dp(208))
        }
        drawer.onNavigate = { target ->
            drawer.close()
            val index = Destination.entries.indexOf(target)
            drawer.setSelected(target)
            if (feature != index || level != 0) navigate(0, index)
        }
        drawer.onSettings = { drawer.close(); if (level != 1) navigate(1) }
        drawer.settleInterpolator = motion.drawerCurve
        picker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>) = Unit
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                drawer.settleInterpolator = PreviewMotion.entries[position].drawerCurve
            }
        }
        setContentView(drawer)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawer.blocksUnderlyingInput) drawer.close() else if (level > 0) navigate(level - 1) else finish()
            }
        })
    }

    private fun rememberScroll() {
        (page as? ScrollView)?.let { scrollPositions[level] = it.scrollY }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        rememberScroll()
        outState.putIntArray("scroll", scrollPositions)
        outState.putInt("game", game)
        outState.putInt("feature", feature)
        outState.putInt("level", level)
        outState.putInt("motion", picker.selectedItemPosition)
        outState.putBoolean("native", systemScroll.isChecked)
    }

    private fun navigate(next: Int, nextFeature: Int = feature) {
        transition?.end()
        (page as? GamePane)?.finishSettle()
        rememberScroll()
        val old = page
        val previous = level
        val vertical = next == 0 && previous == 0
        val overlay = !vertical && (next == 0 || previous == 0)
        val forward = if (vertical) nextFeature > feature else next > level
        level = next
        feature = nextFeature
        page = makePage()
        val incoming = page
        stage.addView(incoming, if (overlay && !forward) 0 else stage.childCount)
        updateActions()
        val distance = dp(32) * if (forward) 1f else -1f
        fun draw(p: Float) {
            if (overlay) {
                val settings = if (forward) incoming else old
                val shown = if (forward) p else 1f - p
                settings.translationY = dp(20) * (1f - shown)
                settings.alpha = shown.coerceIn(0f, 1f)
            } else {
                if (vertical) {
                    old.translationY = -p * distance
                    incoming.translationY = (1f - p) * distance
                } else {
                    old.translationX = -p * distance
                    incoming.translationX = (1f - p) * distance
                }
                old.alpha = (1f - p / .55f).coerceIn(0f, 1f)
                incoming.alpha = ((p - .3f) / .7f).coerceIn(0f, 1f)
            }
        }
        draw(0f)
        transition = motion.animator(0f, 1f, frame = ::draw) {
            stage.removeView(old)
            transition = null
        }
        transition?.start()
    }

    private fun updateActions() {
        systemScroll.visibility = if (level >= 2) View.VISIBLE else View.GONE
        actions.removeAllViews()
        fun action(name: String, handler: () -> Unit) {
            actions.addView(button(name, handler), LinearLayout.LayoutParams(0, -2, 1f))
        }
        if (level == 0) {
            action("切换游戏") { (page as GamePane).settle(1 - game) }
            action("切换功能") { navigate(0, (feature + 1) % 2) }
            action("设置") { navigate(1) }
        } else {
            action("返回") { navigate(level - 1) }
            if (level < 3) action(if (level == 1) "数据与同步设置" else "同步日志") { navigate(level + 1) }
        }
    }

    private fun makePage(): View {
        if (level == 0) return GamePane()
        val body = column().apply {
            setPadding(dp(20), dp(18), dp(20), dp(24))
            addView(label(listOf("", "设置", "数据与同步设置", "同步日志")[level], 24))
            addView(label("第 $level 级", 14))
        }
        if (level < 3) body.addView(button(if (level == 1) "数据与同步设置" else "同步日志") { navigate(level + 1) })
        if (level == 1) return body.apply { setBackgroundColor(paper) }
        val titles = if (level == 2) listOf("成绩同步", "存档管理", "日志导出") else listOf("读取本地存档", "校验记录", "同步完成")
        repeat(48) { i ->
            body.addView(label("${i + 1}　${titles[i % titles.size]}", 16).apply {
                gravity = Gravity.CENTER_VERTICAL
                minHeight = dp(56)
            })
        }
        val offset = scrollPositions[level]
        val scroll = if (systemScroll.isChecked) ScrollView(this) else SettingsPageScrollView(this)
        return scroll.apply {
            setBackgroundColor(paper)
            addView(body)
            post { scrollTo(0, offset) }
        }
    }

    private inner class GamePane : FrameLayout(this@MotionTestActivity) {
        private var position = game.toFloat()
        private var startPosition = position
        private var downX = 0f
        private var tracker: VelocityTracker? = null
        private var animator: ValueAnimator? = null
        private val config = ViewConfiguration.get(context)

        init {
            listOf("舞萌 DX", "中二节奏").forEachIndexed { index, title ->
                addView(column().apply {
                    setPadding(dp(24), dp(24), dp(24), dp(24))
                    setBackgroundColor(if (index == 0) Color.rgb(231, 243, 249) else Color.rgb(251, 243, 219))
                    addView(label(title, 30))
                    addView(label(listOf("成绩同步", "歌曲详情", "敬请期待")[feature], 23))
                    if (feature < 2) addView(label(if (feature == 0) "选择平台　·　同步成绩　·　查看日志" else "曲目信息　·　谱面难度　·　成绩记录", 15))
                    addView(label("左右滑动切换游戏", 14).apply { setPadding(0, dp(32), 0, 0) })
                })
            }
        }

        private fun drawPosition(p: Float) {
            position = p
            for (i in 0..1) getChildAt(i).translationX = (i - p) * width
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            drawPosition(position)
        }

        fun finishSettle() { animator?.end() }

        fun settle(target: Int, velocity: Float = 0f) {
            animator?.cancel()
            game = target
            animator = motion.animator(position, target.toFloat(), velocity, ::drawPosition) { animator = null }
            animator?.start()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    animator?.cancel()
                    tracker?.recycle()
                    tracker = VelocityTracker.obtain()
                    downX = event.x
                    startPosition = position
                }
                MotionEvent.ACTION_MOVE -> {
                    val requested = startPosition * width + downX - event.x
                    val bounded = requested.coerceIn(0f, width.toFloat())
                    val extra = requested - bounded
                    val pull = resistedDragDistance(abs(extra), 0f, dp(GameSwipeDimensions.EDGE_OVERSHOOT_DP).toFloat())
                    drawPosition((bounded + kotlin.math.sign(extra) * pull) / width)
                }
            }
            tracker?.addMovement(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                tracker?.computeCurrentVelocity(1000, config.scaledMaximumFlingVelocity.toFloat())
                val speed = -(tracker?.xVelocity ?: 0f)
                val target = when {
                    event.actionMasked == MotionEvent.ACTION_CANCEL || abs(event.x - downX) < config.scaledTouchSlop -> game
                    position <= 0f -> 0
                    position >= 1f -> 1
                    abs(speed) >= config.scaledMinimumFlingVelocity -> if (speed > 0) 1 else 0
                    position >= .5f -> 1
                    else -> 0
                }
                settle(target, if (event.actionMasked == MotionEvent.ACTION_UP) speed / width else 0f)
                tracker?.recycle()
                tracker = null
            }
            return true
        }

        override fun onDetachedFromWindow() {
            animator?.cancel()
            tracker?.recycle()
            tracker = null
            super.onDetachedFromWindow()
        }
    }

    private fun dp(value: Int) = ViewPrimitives.dp(this, value)
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun label(value: String, size: Int) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(ink)
        setPadding(0, dp(4), 0, dp(4))
    }
    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        setOnClickListener { action() }
    }
}
