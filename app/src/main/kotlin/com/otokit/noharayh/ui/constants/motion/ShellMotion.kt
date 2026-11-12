package com.otokit.noharayh.ui.constants.motion

/**
 * 动画曲线的纯标识。
 *
 * 只登记名称，不实例化 Interpolator；执行器在使用时创建运行对象。
 */
enum class MotionCurve { FastOutSlowIn, OutQuint }

/**
 * 转场效果的纯描述。
 *
 * 只描述允许变化哪类属性，不含时长、View 或 Interpolator；
 * 具体幅度、位移量由各组配置提供，由 animation 层统一解释。
 */
enum class TransitionKind {
    Fade,
    Slide,
    Scale,
    ScaleFadeSlide,
}

/** 标准按压反馈。 */
object PressFeedback {
    const val SCALE: Float = 0.95f
    const val PRESS_DURATION_MS: Long = 90L
    const val RELEASE_DURATION_MS: Long = 120L
}

/** 共用默认节奏；页面转场与外围反馈分别有明确入口。 */
object MotionDefaults {
    val PAGE_CURVE: MotionCurve = MotionCurve.OutQuint
    val CHROME_CURVE: MotionCurve = MotionCurve.FastOutSlowIn
    val EMPHASIZED_CURVE: MotionCurve = MotionCurve.FastOutSlowIn
}

/** 同一次功能切页的唯一类型与节奏；内容和标题共享进度。 */
object NavigationTransition {
    val KIND: TransitionKind = TransitionKind.ScaleFadeSlide
    const val DURATION_MS: Long = 300L
    val CURVE: MotionCurve = MotionDefaults.EMPHASIZED_CURVE
}

/** 导航内容只描述自己的空间幅度。 */
object NavContentTransition {
    const val SCALE: Float = 0.85f

    /** 纵向位移占安全内容高度的比例。 */
    const val TRANSLATION_HEIGHT_FRACTION: Float = 0.4f
}

/** 手机标题的局部幅度，量纲与内容区解耦。 */
object NavTitleTransition {
    /** 原组合模式保持原来的等比例字形；纯 Scale 模式使用明确的缩放幅度。 */
    const val COMBINED_SCALE: Float = 1f
    const val SCALE_ONLY: Float = 0.85f
    const val TRANSLATION_ROW_HEIGHT_FRACTION: Float = 0.76f
    const val TRANSLATION_MAX_DP: Float = 44f
}

/** 双页联动只映射 Pager 进度到属性；不在消费者中增加第二份计时或曲线。 */
object PagerPairTransition {
    val KIND: TransitionKind = TransitionKind.ScaleFadeSlide
    val CURVE: MotionCurve = MotionDefaults.EMPHASIZED_CURVE

    /** 横移距离占容器宽度的比例。 */
    const val TRANSLATION_RATIO: Float = 0.7f
    const val MIN_SCALE: Float = 0.7f
    const val MIN_ALPHA: Float = 0f
}

/** 仅替换抽屉收尾曲线；跟手进度与原生按距离/速度计算的时长保留。 */
object DrawerTransition {
    val CURVE: MotionCurve = MotionDefaults.EMPHASIZED_CURVE
}

/** 进出设置时的整体过渡。 */
object SettingsTransition {
    val KIND: TransitionKind = TransitionKind.ScaleFadeSlide
    const val ENTER_DURATION_MS: Long = 420L
    const val EXIT_DURATION_MS: Long = 300L

    /** 功能页缩小到的比例。 */
    const val CONTENT_SCALE: Float = 0.70f

    /** 覆盖层入场起始放大比例。 */
    const val OVERLAY_SCALE_FROM: Float = 1.15f

    /** 组合模式沿用当前无纵移的设置手感；纯 Slide 模式有独立可调的空间幅度。 */
    const val COMBINED_CONTENT_TRANSLATION_FRACTION: Float = 0f
    const val COMBINED_OVERLAY_TRANSLATION_FRACTION: Float = 0f
    const val SLIDE_CONTENT_TRANSLATION_FRACTION: Float = 0.15f
    const val SLIDE_OVERLAY_TRANSLATION_FRACTION: Float = 0.2f

    val CURVE: MotionCurve = MotionDefaults.PAGE_CURVE
}

/** 设置标题在功能态与设置态之间的缩放量纲。 */
object SettingsTitleTransition {
    const val SCALE_SHRINK: Float = 0.4f
    const val SCALE_EXPAND_FROM: Float = 1.16f
    const val COMBINED_TRANSLATION_HEIGHT_FRACTION: Float = 0f
    const val SLIDE_TRANSLATION_HEIGHT_FRACTION: Float = 0.35f
}

/** 主题色变化（装饰三角、菜单圆底）的统一过渡。 */
object ChromeThemeTransition {
    const val DURATION_MS: Long = 260L
    val CURVE: MotionCurve = MotionDefaults.CHROME_CURVE
}

/** 菜单/返回图标的交叉淡变。 */
object ChromeIconTransition {
    val KIND: TransitionKind = TransitionKind.Fade
    /** 只在启用缩放/位移类型时使用；默认 Fade 保持原来的纯交叉淡变。 */
    const val SCALE_FROM: Float = 0.85f
    const val TRANSLATION_SIZE_FRACTION: Float = 0.25f
    const val DURATION_MS: Long = 200L
    val CURVE: MotionCurve = MotionDefaults.CHROME_CURVE
}
