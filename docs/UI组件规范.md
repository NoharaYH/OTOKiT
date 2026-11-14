# UI组件规范

本文用于统一现有按钮及其承载容器，为后续页面提供可直接复用的组件约定。当前范围为通用交互动效与设置页面；新类型按实际需求补充。

本文同时记录目标约定与已核实的使用契约。标记“可直接复用”的能力使用现有接口；标记“待重构”的能力尚无完整通用入口，不得通过复制业务页面实现来接入。以下路径均以 `app/src/main/kotlin/com/otokit/noharayh/ui/` 为代码根目录；未另注明时，类名对应其包目录下的同名 Kotlin 文件。核对日期：2026-11-14。

## 总则：公共能力、标准组件、页面使用

本文的归一化目标是三层单向依赖：设置页面使用标准组件，标准组件组合公共能力。页面不直接装配组件内部的效果，公共能力不依赖具体页面或业务组件。

### 1. 公共能力层

- 分别承载震动、内缩、涟漪、指示点、胶囊化，以及可复用的尺寸过渡、圆角过渡、内容入退场等能力。
- 每项能力明确自己的参数定义、默认值、执行逻辑、输入输出及取消清理职责。颜色、幅度、阶段时间、曲线、阻尼和刚度应能追溯到对应能力的参数来源。
- 公共字体、色彩等非交互基础能力也在公共层管理；组件自己的高度、槽位和内边距不因此变成全局参数。
- 几何、当前状态和目标状态由消费组件提供，公共效果不读取设置业务状态。多个效果可以共享明确的进度输入，但不能因此失去各自的参数与执行职责边界。
- “效果分类”不强制对应一个文件；要求的是职责可定位、参数有唯一归属、引用明确，不在多个组件中复制实现。

### 2. 标准组件层

- 本文规定的父级菜单按钮、复合容器、交互容器、组合容器、内部按钮、DMI、纯信息框，均应形成供页面直接使用的成品组件。
- 标准组件按需组合公共能力，并拥有自己的默认尺寸、布局、样式和状态契约。纯信息框不接入按压效果，复合容器也不因内部存在按钮而获得整容器点击行为。
- 组件负责将触摸、确认状态及分组位置转换为效果输入，协调同步动画、内容测量、裁剪、绘制层级、无障碍状态、禁用、中断与卸载。
- 组件内部使用的常规布局及效果接线不能转交每个设置页面重复实现。依赖父级分组协调时，由标准分组宿主统一处理，不在页面手动计算相邻圆角。
- 每项组件参数必须说明：归属组件自身还是公共能力、默认来源、允许覆盖范围。公共参数作为默认值来源，不允许为一个实例直接修改共享常量。

### 3. 设置页面层

- 页面只提供图标、文字、内容、确认的业务状态、操作回调，以及页面级排列与分组关系。
- 页面直接使用标准组件，不自行创建组件内部动画，不计算按压圆角、不维护展开中的逐帧高度，也不复制效果参数。
- 用户操作由组件报告意图，页面或状态持有者确认后回传；组件负责呈现确认状态及过渡。
- 第三、四节中的现有底层调用示例是当前实现接入说明，不表示未来页面应逐项拼接这些底层能力。标记“可直接复用”只说明现有功能可用，不代表已完成三层归一化。

### 4. 各标准组件的能力组合

- **父级菜单按钮：** 五项交互效果、图文布局与选中配色呈现；拥有独立高度和间距规范。
- **复合容器：** 标题及内容槽位、静态布局与分组形态；按需组合尺寸过渡、内容入退场和圆角过渡，不接入整容器按压效果。
- **交互容器：** 五项交互效果、标题及状态控件布局、开关状态过渡、分组形态；容器自身拥有默认行高和内边距。
- **组合容器：** 交互标题行与可选内容槽位，组合五项交互效果、展开布局、内容入退场及分组形态；组件内部协调标题反馈与展开底形。
- **容器内按钮：** 五项交互效果及按钮自身布局；选择型变体追加确认状态、选择标记和选中形态过渡。
- **纯信息框：** 只读信息布局、文字与静态底形，不接入五项交互效果。
- **DMI：** 封装选择框、菜单和配套交互，保留现有外观；其完整页面接入边界仍需按现状标注，不在本轮重做专属效果。
- **颜色滑块：** 独立组件，不作为按钮或容器的通用效果来源。

### 5. 参数与实现的归属规则

- 公共效果参数与执行器分别归入 `constants/motion` 和 `animation/common` 等对应公共目录；这是目标归属，现有例外在后文注明，未建立的文件不作为可用 API。
- 标准组件尺寸归入 `constants/dimensions` 下该组件自己的参数定义；需要独立的组件状态动效参数时，放入对应组件的 motion 参数定义，不借用主题选择或调色器的参数表。
- 标准组件位于 `kit`，负责引用公共参数、效果及基础样式。页面级业务选择和页面排列留在 `settings`。
- 每个效果记录“功能、参数及单位、参数入口、执行入口、使用及清理约束”；每个组件记录“结构、默认参数、能力组合、状态与分组、接入接口、当前差距”。
- 通用化应迁移现有实现与参数，保留已确认的视觉行为；不得以尚未存在的文件或方法冒充已完成的接口。

## 一、通用交互动效

### 1. 触觉反馈

- **简称：** 震动。
- **定义：** 在指定交互阶段触发系统触觉反馈，以确认用户操作。
- **阶段：** 按下反馈与抬手反馈分别描述；抬手反馈指有效点击被接受后的释放反馈，手势取消不等同于有效抬手。
- **约定：** 各组件明确启用的阶段；“震动”不表示自定义震动波形，也不表示点击动作必须等待反馈结束。

### 2. 按压内缩

- **简称：** 内缩。
- **定义：** 按压过程中，组件的可见轮廓向内部收缩，释放或取消后恢复静止形态。
- **约定：** 属于视觉几何变化，不表示布局尺寸或触摸区域同步缩小；与整组件缩放分别描述。

### 3. 外部弹性涟漪

- **简称：** 按下涟漪／涟漪。
- **定义：** 按压触发组件外侧的黑色反馈轮廓，通过弹性动画扩张、回弹和退场。
- **约定：** 本文“涟漪”专指外部效果，不指内部指示点，也不指静态描边或阴影。宿主应为外溢绘制预留空间，并处理裁剪及相邻组件的遮挡。

### 4. 触点跟随指示

- **简称：** 指示点。
- **定义：** 在组件内部绘制黑色圆形反馈，通过弹性运动跟随手指位置，用于指示当前触点。
- **约定：** 跟随、尺寸变化及释放退场属于该效果；指示点不代表组件的选中状态，与外部涟漪独立配置。

### 5. 胶囊形态过渡

- **简称：** 胶囊化／囊化。
- **定义：** 增大组件轮廓的圆角半径，使其由普通圆角矩形连续过渡为胶囊形；反向过渡恢复原圆角。
- **约定：** 胶囊化不表示组件尺寸增大。触发条件由组件指定，例如选中状态变化，不默认与按下绑定。

### 范围说明

以上五项作为当前通用动效的基础分类。现有实现还包含白色亮度覆盖及颜色、透明度过渡，暂不单列为通用动效；需要时在具体组件规范中描述。

下拉选择组件沿用现有样式和专属交互，不在本轮重新定义展开、滚动与吸附行为。颜色滑块作为独立组件维护。

## 二、设置页面组件规范

### 1. 父级菜单按钮

- **职责：** 设置父级页面的分类导航入口。
- **交互：** 采用震动、内缩、涟漪、指示点和胶囊化五项通用效果。
- **布局：** 高度、间距独立定义，保留父级页面的视觉层级，不套用子页面容器的默认高度和分组间距。
- **现有复用基础：** `SettingsOptionView`。

### 2. 子页面容器

#### 2.1 复合容器

- **职责：** 将标题、辅助操作和可选内容组织为一个展示单元；容器本身不响应点击。
- **结构：** 一个图标、位于图标右侧的主标题、可选的标题行尾部组件，以及可选的下方内容区。
- **事件边界：** 内部按钮或选择框独立响应交互，点击容器空白区域不触发内部操作。
- **空内容状态：** 下方没有组件时仅保留标题行，不保留空内容区及其间距；标题行右侧仍可放置操作组件。
- **使用位置：** 应用信息中的卡片、个性化中的主题选择卡片、应用设置中的应用显示比例卡片。无主题模式下的主题选择卡片可仅显示标题行及右侧三个模式按钮。
- **现有复用基础：** `SettingsPageViews.card` 及个性化页的主题卡片装配。

#### 2.2 交互容器

- **职责：** 容器本身承载一个设置操作，整行作为统一交互入口。
- **结构：** 标题及状态控件，不配置用于标识容器类型的图标。允许按需附加警告图标，例如硬件加速标题旁的警告标识；警告图标属于提示信息，不改变容器分类。
- **交互：** 采用震动、内缩、涟漪、指示点和胶囊化五项通用效果；行与状态控件表达同一个操作，不重复触发。
- **使用位置：** 背景跟随配色、硬件加速、HTML 动画。
- **现有复用基础：** `GroupedSwitchRow`。

#### 2.3 组合容器

- **职责：** 组合交互容器的可操作标题行与复合容器的可选内容区。
- **结构：** 可交互标题行及其下方可展开内容，形成连续的容器外观。
- **交互：** 标题行采用交互容器的五项效果；展开内容中的组件独立处理操作，不能因操作子组件而误触标题行。
- **涟漪：** 复用现有展开状态下的涟漪绘制与层级处理，保留标题行反馈范围以及展开内容边界的衔接。
- **使用位置：** 个性化 → 主题选择 → 无主题 → 从壁纸取色。
- **现有复用基础：** `ExpandableSettingsRow` 及其展开内容装配。

### 3. 容器布局规则

#### 3.1 分组与圆角

- 按容器类型及所属设置组合组织分组，采用 Material 3 风格的成组外观：组内间距小于组间间距，相邻边使用小圆角，组外轮廓保留大圆角。
- 同类型且连续的容器优先成组；属于同一设置组合的不同容器也可成组，例如无主题模式展开后的三个容器。
- 组首保留顶部外圆角，组尾保留底部外圆角，内部相邻边使用内圆角；单个容器保留完整外圆角。
- 展开、收起或显隐改变分组成员时，圆角随实际相邻关系更新。
- 现有分组样例：无主题模式下的三个容器，以及硬件加速与 HTML 动画两个容器。
- 主题菜单中未折叠选项采用类似的分组原则，但属于下拉选择组件，不纳入本节容器分类，也不直接套用容器尺寸。

#### 3.2 默认高度

- 子页面容器的默认高度为 **52dp**，以当前硬件加速开关行的高度为基准。
- 52dp 是默认单行基准，不是对所有内容的强制最大高度。当前开关行使用最小高度；长标题、大字体或尾部内容超出时的测量策略需由标准组件明确，不能以固定高度直接裁切。
- 适用状态：原始、未展开的交互容器；下方没有内容组件的复合容器；下方没有内容组件或处于收起状态的组合容器。
- 标题行右侧存在按钮或开关，不视为下方存在内容组件，不改变上述默认高度。
- 有下方内容时，容器高度随内容和内边距扩展，不将整个容器限制为 52dp。
- 52dp 不适用于父级菜单按钮、容器内按钮或下拉菜单选项。
- 数值依据：当前 `GroupedSwitchDimensions.ROW_HEIGHT_DP` 固定为 `52`（原先为 `28 + 12 × 2`）。本文固定记录为 52dp，不依赖未来代码表达式的变化。

### 4. 容器内组件

#### 4.1 按钮

- **职责：** 承载操作或单选意图，可表现为整行按钮或横向排列的选择按钮。
- **交互：** 统一采用震动、内缩、涟漪、指示点和胶囊化五项通用效果。各效果的触发阶段及与选中状态的关系须在后续参数规范中明确，不能仅因视觉相似而混用。
- **使用位置：** 应用显示比例中的三个选项、主题选择标题行右侧的三个模式按钮，以及应用信息卡片中的操作按钮。
- **现有复用基础：** `SettingsPageViews.action`、`SettingsPageViews.choice`、`SettingsSegmentedChoice`。
- **展示边界：** 只读内容使用纯信息框，不作为按钮处理。

#### 4.2 复合选择框

- **文档名称：** 复合选择框；别名 `DropdownMenuItem`，简称 `DMI`。这是本文约定的组件名称，不表示直接使用框架中的同名组件。
- **职责：** 组合当前值展示、展开入口和选项菜单。
- **规范：** 直接复用已完成的样式及交互，不在本轮重新设计。
- **使用位置：** 个性化中的主题选择。
- **现有复用基础：** `LabeledDropdownField`、`ThemeSelectorPanel`、`ThemeDropdownMenu` 及配套滚动条。

#### 4.3 纯信息框

- **职责：** 展示只读信息，不承载操作或选择意图。
- **结构：** 圆角背景内排列信息名称与信息值，名称在左、信息值在右。
- **交互：** 不响应点击，不启用震动、内缩、涟漪、指示点或胶囊化；不呈现可点击或可选择语义。
- **使用位置：** 应用信息中的版本信息。
- **现有复用基础：** `SettingsPageViews.info`。

### 5. 独立组件：颜色滑块

颜色滑块作为独立组件维护，不归入按钮或容器规范。沿用现有通道渐变、数值输入联动和拖动交互；本轮不扩展其规范。现有实现为 `ColorChannelSlider`。

### 6. 当前实现与规范的关系

`PressSurface` 已提供按压胶囊化、内缩、震动与指示点，并可开启外部涟漪。继承或组合它的按钮无需重新实现这些效果。选中状态的胶囊化属于另一种状态表达，不能与按压胶囊化混为一谈。统一工作主要涉及参数归属、状态契约和动态容器能力，而非重新编写五套效果。

## 三、通用效果使用契约

### 1. 公共接入与参数边界〔可直接复用〕

- **入口：** `kit/common/PressSurface`，继承 `FrameLayout`，可直接承载文字、图标或布局。
- **单位：** 实例属性的半径、直径与内缩量均为 px；调用方负责 dp 换算。时长为 ms，进度和白色层透明度为 0..1，颜色为 ARGB。
- **线程：** 在 UI 线程更新属性。绘制属性自动刷新，不负责调整布局尺寸。
- **配置层级：** 实例属性用于已有的组件差异；`PressSurfaceSpec` 和 `constants/motion/PressHaloSpec` 是共享默认参数。修改共享参数会影响所有消费者。
- **生命周期：** 触摸、释放与反馈清理由 `PressSurface` 接线，页面只绑定点击意图，不重复播放相同震动或动画。

```kotlin
val density = context.resources.displayMetrics.density
val button = PressSurface(context).apply {
    fillColor = android.graphics.Color.WHITE
    baseRadius = 4f * density
    outerHaloEnabled = true
    contentDescription = "操作名称"
    setOnClickListener { onAction() }
}
```

示例中的 `context` 与 `onAction` 由调用方提供；实际按钮还需添加内容、设置布局尺寸，并满足下述外溢绘制约束。现有成品按钮应优先使用，不为每个页面重新组装底层。

### 2. 震动〔可直接复用〕

- **职责：** 确认按下和有效点击释放；不表达业务成功与失败。
- **执行位置：** `animation/common/PressHaptics`；由 `PressSurface` 连接触摸与点击。
- **参数位置：** `constants/motion/PressSurfaceSpec`。
- **按下反馈：** `VIRTUAL_KEY`。
- **释放反馈：** Android 30 及以上使用 `GESTURE_END`，较早版本回退到 `CLOCK_TICK`。
- **最小按下／释放间隔：** `MIN_HAPTIC_GAP_MS = 120ms`，快速点击补足间隔。
- **实例入口：** `releaseHapticEnabled` 默认 true；设为 false 只关闭释放反馈。系统触觉开关仍由 View 与系统设置约束。
- **执行顺序：** `deferClickUntilReleaseFeedback` 默认 false。需要交出窗口时可设为 true，在释放反馈后额外留出 `RELEASE_FEEDBACK_SETTLE_MS = 60ms` 再执行点击；这不是所有点击的统一延迟。
- **使用要求：** 取消手势不能当作有效点击；不要在点击回调里再次补发同一触觉。

### 3. 内缩〔可直接复用〕

- **职责：** 改变可见底形，不改变布局和触摸区域。
- **实例入口：** `pressInset`，默认每边 `3dp` 换算成 px；设为 0 可去掉内缩量，但不会关闭其他效果。
- **参数位置：** `PressSurfaceSpec.INSET_DP`、`PRESS_DAMPING = 0.85`、`PRESS_STIFFNESS = 1000`、`MIN_VISIBLE_CHANGE = 0.002`。
- **执行位置：** `animation/common/PressProgress` 提供共享进度，`PressGeometry` 计算内缩及圆角，`PressSurface` 使用结果绘制。
- **时序：** 按下目标进度为 1，释放回到 0；由弹簧收敛决定结束时间，没有固定总时长。
- **耦合边界：** 内缩、按压胶囊化和白色反馈层使用同一按压进度。参数与执行已分离至公共层；标准组件通过 `PressSurface` 组合，不由页面分别启动。

### 4. 涟漪〔可直接复用〕

- **职责：** 在组件外侧绘制随形状变化的半透明黑色反馈。
- **实例入口：** `PressSurface.outerHaloEnabled = true`；默认 false。
- **执行位置：** `animation/common/PressHalo`；调用方通过 `PressSurface` 使用，不自行连接 `press()`、`release()`、`draw()` 和 `reset()`。
- **参数位置：** `constants/motion/PressHaloSpec`。
- **颜色：** 黑色，`ALPHA = 80`，即 80/255 不透明度。
- **厚度来源：** 稳定外扩量 `REST_OUTSET_DP = 6dp`，峰值目标外扩量 `PEAK_OUTSET_DP = 9dp`。实际为扩大黑色底层后覆盖内部填充，没有独立的描边 `strokeWidth`。
- **形状输入：** 反馈宽高、当前内缩量、上下圆角由 `PressSurface` 每帧提供；涟漪不拥有组件的圆角状态。
- **扩张：** 阻尼比 0.8、刚度 1600。
- **持续按压回落：** 阻尼比 0.9、刚度 300。
- **释放收缩：** 阻尼比 1.0、刚度 700。
- **阶段切换：** `EXPAND_DURATION_MS = 120ms` 后回落；快速抬手等待剩余阶段时间后收缩。120ms 不是总动画时长。
- **收敛阈值：** `MIN_VISIBLE_CHANGE = 0.002`，作用于扩张进度。
- **配置限制：** 当前只提供实例级启停，不提供实例级粗细、颜色或弹簧参数覆盖；不要为单个按钮直接修改共享 Spec。

**外溢与遮挡约束：** 相关父容器需设置 `clipChildren=false`、`clipToPadding=false`。相邻行使用 `kit/common/PressFrontColumn` 提升按压行的绘制顺序；该容器不改变布局顺序、不自动设置组间距或圆角。

```kotlin
val column = PressFrontColumn(context)
column.addView(button) // 直接子 PressSurface 自动绑定

// 若按钮嵌在 column 的某个直接子行中，则在装配后显式绑定：
val unbind = column.bind(nestedButton)
// 只移除内部按钮时调用 unbind()；移除整行由 column 自动解绑。
```

### 5. 指示点〔可直接复用〕

- **职责：** 在组件内部指示触点；裁切在当前底形内，不代表选中状态。
- **入口：** `PressSurface.innerRippleEnabled` 默认 true。关闭后不影响内缩、触觉和点击。
- **颜色：** 黑色，`PressSurfaceSpec.BLACK_ALPHA = 56`，即 56/255 不透明度。
- **尺寸：** `rippleMaxDiameter` 默认 120dp，`rippleRestDiameter` 默认 106dp，实例赋值使用 px。小按钮会裁切圆形，不需要将按钮放大到该直径。
- **尺寸动画：** 扩张弹簧 0.8/1600，回落 0.9/300，释放 1.0/700；数值依次为阻尼比／刚度，来源为 `PressSurfaceSpec`。
- **阶段时间：** `rippleExpandMs` 默认 120ms；不是固定总动画时长。
- **位置跟随：** `followTauMs` 默认 30ms 且应大于 0，采用 `1 - exp(-Δt / τ)` 指数平滑。位置本身不是弹簧运动，弹性来自尺寸变化。
- **数量：** `maxRipples` 默认 8，限制内部反馈实例数量。
- **执行位置：** `animation/common/TouchIndicator` 管理尺寸弹簧、位置跟随和清理；`PressSurface` 连接手势并绘制。修改最大直径、阶段时间或数量配置不会重启已有动画。

### 6. 胶囊化〔按压能力可直接复用〕

- **按压胶囊化：** `PressSurface` 随按压进度将静止圆角插值到内缩后可用短边的一半；释放恢复静止圆角，与内缩共用 0.85/1000 按压弹簧。
- **几何入口：** `baseRadius` 为静止圆角；`topCornerRadius`、`bottomCornerRadius` 可分别覆盖，null 表示沿用基础圆角，均为 px。
- **展开衔接：** `bottomCornerPressFraction` 控制底部按压圆角变化比例，默认 1。组合容器可据展开进度约束该值；普通按钮不要自行修改。
- **状态胶囊化：** 选中后保持胶囊形由具体选择组件维护，不由按压状态代替。分段按钮已内置；普通操作按钮释放后不会因此保持选中。
- **配置边界：** 按压几何已抽离为 `PressGeometry`，与 `PressProgress` 组合使用；尚无独立按压胶囊化开关。标准按钮通过 `PressSurface` 消费，页面不操作进度。

### 7. 辅助反馈与独立动效边界

- 白色覆盖层通过 `whiteMaxAlpha` 配置，默认 0.16，跟随按压进度；不是第六种按钮类型。
- 通用开关指示动画由 `animation/common/SwitchTransitionRunner` 执行，`constants/motion/SwitchMotion.DURATION_MS = 200ms`，曲线 `FastOutSlowIn`。成品开关已连接，页面无需直接创建执行器。
- 曲线解析入口为 `animation/common/MotionInterpolators.of(MotionCurve.FastOutSlowIn)`，`MotionCurve` 位于 `constants/motion`。它只提供插值器，不提供内容过渡的布局和生命周期管理。
- 通用内容过渡由 `animation/common/ContentTransition<T>` 执行；标准组件通过 `ExpandableContent` 组合测量、入退场和输入门控。分组圆角由 `GroupShape` 执行。公共时长和曲线来自 `ContainerMotion`；原 `ThemeCardTransitionRunner` 已移除，调色器执行器不作为容器依赖。

## 四、组件接入规范

### 1. 父级菜单按钮〔可直接复用〕

- **入口：** `kit/settings/SettingsOptionView(context, title, icon, accent)`。
- **组成：** 最小高度 64dp；40dp 图标底内放置 24dp 图标，标题 17sp；当前入口间距由宿主设置为 12dp，不写入子页面容器规范。
- **效果来源：** 继承 `PressSurface`，外部涟漪默认开启，复用第三节五项效果。
- **状态：** `setSelectionProgress(0..1)` 更新填充、图标底、图标与文字的反色程度。该方法仅渲染，不自行启动选中动画，不代表持久化选择。
- **使用：** 构造时提供内容及强调色，通过 `setOnClickListener` 发出导航意图；宿主更新选中进度并控制布局。
- **分组与展开：** 不套用子页面成组容器规则，不承担内容展开。
- **参数归属：** 父级按钮尺寸已迁至 `SettingsMenuDimensions`；保留父级独立高度与间距。

### 2. 复合容器〔可直接复用〕

- **入口：** `kit/settings/SettingsCard(context, icon, title, content = null, trailing = null)`；`SettingsPageViews.card` 为兼容工厂；`icon`、`title` 分别为图标和字符串资源 ID。
- **组成：** 图标在左、标题在右，尾部组件占标题行剩余空间，下方放内容 View；容器不可点击。
- **参数来源：** `constants/dimensions/SettingsCardDimensions` 提供白底容器的 16dp 圆角、12dp 内边距。28dp 标题行最小高度、22dp 图标、8dp 图文间距、16sp 粗体标题亦已归入该参数定义。
- **交互来源：** 容器不使用五项按压效果，子组件按自身契约使用。卡片已关闭自身子项裁剪，外层祖先仍需允许外溢。
- **独立状态：** 当前 `card()` 创建四角 16dp 的静态卡片。
- **成组状态：** 目标为组外 16dp、相邻边 4dp；`SettingsCard.setGroupPosition` 接受分组位置；`SettingsGroup` 按可见直接子项自动维护位置和 4dp 间距。
- **内容高度：** `content` 可省略，`setContent(null)` 移除内容及其间距，默认标题区为 52dp；`setContent(view)` 安装内容及 12dp 顶部间距。
- **展开及切换：** `setContentExpanded(expanded, animate)` 控制槽位高度及内容入退场；`replaceContent(view, animate)` 在展开时先退出旧内容再进入最新内容，收起时直接替换且保持收起。`setContent(view)` 直接安装内容，不播放替换动画。
- **引用规则：** 页面使用 `SettingsCard` 的内容及展开接口；内部引用 `ExpandableContent` 与 `GroupShape`，不从主题卡片复制动画。

```kotlin
val card = SettingsPageViews.card(
    context = context,
    icon = iconRes,
    title = titleRes,
    content = contentView,
    trailing = trailingView,
)
```

以上变量由宿主提供；`trailingView` 可为 null。初次创建直接呈现内容，不隐式播放入场。需要动态变化时调用 `card.setContentExpanded(false)` 或 `card.replaceContent(nextContent)`，无需创建动画对象。

### 3. 交互容器〔可直接复用〕

- **入口：** `kit/common/toggle/GroupedSwitchRow`。
- **组成与参数：** 52dp 行高、20dp 文字内边距、右侧 52×32dp 开关；参数来自 `constants/dimensions/GroupedSwitchDimensions`。组内间距 4dp 由宿主布局设置。
- **分组输入：** `groupStart=true, groupEnd=true` 表示独立；true/false 表示组首；false/false 表示组中；false/true 表示组尾。顶部、底部分别选择 16dp 外圆角或 4dp 内圆角。
- **图标：** 默认不提供标识图标；警告可通过 `labelIconRes`、`labelIconTint` 附加，文字色通过 `labelColor` 设置。
- **交互来源：** 继承 `PressSurface` 并开启涟漪；开关指示使用第三节的通用开关动画。
- **状态契约：** `onCheckedChange` 只报告操作意图；外部确认后调用 `setChecked`。不在点击时自行确认状态，无障碍状态反映确认值。
- **生命周期：** 内部管理按压与开关动画；外部控制 `isEnabled`，不用同时给右侧指示器再绑定点击。
- **分组更新：** 支持 `setGroupPosition` 原位更新；由 `SettingsGroup` 自动维护可见成员。分组位置通过 `GroupShape` 连续过渡，首次测量直接呈现目标圆角；普通开关行不承担内容展开。

```kotlin
val row = GroupedSwitchRow(
    context = context,
    label = "设置名称",
    groupStart = true,
    groupEnd = true,
    checked = confirmedValue,
    onCheckedChange = { requested -> onRequested(requested) },
)
// 收到外部确认值后更新：
row.setChecked(newConfirmedValue)
```

### 4. 组合容器〔可直接复用〕

- **入口：** `kit/settings/ExpandableSettingsRow(context, label, checked, onCheckedChange)`，继承标准交互容器。
- **内容：** `setContent(view)` 安装内容，`setExpanded(expanded, animate)` 控制展开；开关确认状态仍通过 `setChecked` 更新，是否展开由业务含义决定，不默认与 checked 同向绑定。
- **自身布局：** 标题行默认 52dp，按实际测量高度布置下方槽位；内容自然高度由 `ExpandableContent` 测量，不接收页面计算的逐帧高度。
- **交互组合：** 标题行使用五项公共效果；内容独立处理点击，标题空白处不会吞掉内容操作。外环反馈使用标题高度，展开进度约束底部按压圆角，保留已有涟漪衔接。
- **展开组合：** 使用 `ExpandableContent` 的高度、透明度及位移过渡；状态、取消与卸载规则见第六节。
- **分组：** 实现 `GroupedSurface`，可直接加入 `SettingsGroup`。单独使用时设置 `GroupPosition.Single`；构造默认两端内圆角，以兼容原组合位置。
- **禁用：** `isEnabled` 控制标题，`setContentEnabled(enabled)` 控制内容槽位输入。组件组合者可通过 `onExpansionChanged` 同步依赖展开完成的其他状态；页面无需逐帧驱动效果。

```kotlin
val row = ExpandableSettingsRow(context, label, confirmed, onRequested)
row.setContent(contentView)
row.setChecked(confirmed)
row.setExpanded(showContent, animate = false) // 首次呈现
// 后续收到确认状态：
row.setChecked(nextConfirmed)
row.setExpanded(nextShowContent)
```

### 5. 容器内按钮〔现有变体可直接复用〕

**操作行：** `SettingsPageViews.action(context, label, external = false, onClick)`。40dp 内容行、4dp 静止圆角、文字与右侧箭头／外链图标，内置五项按压效果；`external=true` 同时选择外链图标并启用释放反馈后执行。若操作会重建或交出窗口，也可在返回的 `PressSurface` 上单独设置 `deferClickUntilReleaseFeedback=true`。当前布尔参数同时控制图标与执行时机，解耦属于后续重构，不必为既有使用先重写。

**单选行：** `SettingsPageViews.choice(context, label, selected, onClick)`。40dp 行高，未选中 4dp、选中 20dp 圆角，右侧选择标记；单选语义在创建时配置。工厂返回 `SettingsButton`，通过 `renderSelection(selected, animate)` 更新确认状态、标记、文字与圆角；不能只改 `isSelected`。

**分段选择：** `kit/settings/SettingsSegmentedChoice`。26dp 高、4dp 间距、12sp 文字，按文字计算宽度；未选中圆角 4dp、选中 13dp。五项按压效果由内部 `PressSurface` 提供，选中形态由组件自身维护。

```kotlin
val choices = SettingsSegmentedChoice(context)
choices.setItems(labels, confirmedIndex) { requestedIndex ->
    onSelectionRequested(requestedIndex)
}
// 外部确认后回传；首次或无需动画时 animate=false：
choices.setSelection(newConfirmedIndex, animate = true)
```

- **分段状态动效：** 内部 `SettingsChoiceTransitionRunner` 将静止圆角和文字颜色过渡到确认状态；220ms、`FastOutSlowIn`。调用方只用 `setSelection`，无需引用该执行器。
- **当前参数归属：** 尺寸来自 `SettingsChoiceDimensions`，时长、禁用配色比例与曲线来自 `SettingsChoiceMotion`，已脱离主题参数。
- **禁用：** `setControlsEnabled` 控制实际操作，`setDisabledProgress` 仅控制视觉淡化，不能相互代替。
- **卸载：** 内部停止动画并恢复到确认状态；调用方不保留动画对象。
- **尺寸边界：** 26dp 和 40dp 是视觉尺寸，当前不代表所有入口已经提供 48dp 热区；不在文档整理时擅自修改布局。

### 6. 纯信息框〔可直接复用〕

- **入口：** `SettingsPageViews.info(context, label, value)`。
- **结构：** 左侧名称、右侧值，浅灰底，静止圆角 4dp，水平内边距 16dp，最小高度 40dp，来自 `SettingsCardDimensions.CONTENT_ROW_HEIGHT_DP`；最小高度不等于固定高度。
- **状态与动效：** 只读，不使用五项反馈，不支持选择或展开；内容变更当前通过重新创建或外部持有的视图装配处理，没有专用 `setValue` 接口。
- **引用：** 将返回 View 作为 `card()` 的内容；无需创建动画执行器或点击回调。

### 7. DMI 与颜色滑块的边界

- **DMI：** 当前主题场景可复用已有整套选择框／面板／菜单。内部使用 `ThemeOption`，并需要宿主连接高度、返回预览和滚动条外部热区，不是可直接放入任意数据的通用 Spinner。任意业务数据的 DMI 接口仍待重构；本轮不重新定义其已完成的样式和专属动画。
- **颜色滑块：** `ColorChannelSlider` 独立维护，通过 `onGrab`、`onRelease`、`onValue` 通知交互，`renderRamp(colors)` 更新渐变。它不是按钮效果入口，本轮不展开调色交互规范。

## 五、完成状态与剩余边界

1. **公共效果职责〔已完成〕：** `constants/motion/PressSurfaceSpec`、`PressHaloSpec` 提供默认参数；`PressHaptics`、`PressProgress`、`PressGeometry`、`TouchIndicator`、`PressHalo` 分担执行职责，`PressSurface` 负责组合和触摸适配。共享按压进度保留，不要求页面分别启动五项效果。
2. **组件默认参数〔已完成基础归属〕：** `SettingsMenuDimensions` 管理父级按钮，`SettingsButtonDimensions` 管理内部按钮，`SettingsChoiceDimensions/Motion` 管理分段按钮，`SettingsCardDimensions` 管理复合卡片，`GroupedSwitchDimensions` 管理开关，`SettingsGroupDimensions` 管理成组圆角及间距。`SettingsComponentColors` 提供标准内容按钮与卡片配色。
3. **按钮状态〔已完成〕：** `SettingsButton.renderSelection` 原位更新单选确认状态；分段按钮继续使用 `setSelection`。操作按钮和单选按钮由现有工厂兼容返回标准 `SettingsButton`。
4. **复合与组合容器〔已完成〕：** `SettingsCard` 支持可选内容、展开与替换；`ExpandableSettingsRow` 封装标题行和展开槽位。具体内容不参与其内部动画执行。
5. **分组〔已完成〕：** `SettingsGroup` 管理可见直接子项和间距；`GroupShape` 管理上下边圆角过渡。标准组合组件需要跨嵌套结构共享进度时，通过 `GroupedSurface.renderGroupEdges` 应用同一帧，不自行计算圆角。`PressFrontColumn` 只负责绘制层级。
6. **页面接入〔已迁移〕：** 应用信息和应用设置经兼容工厂消费标准卡片与按钮；应用设置开关组使用 `SettingsGroup`；父级入口消费独立参数；个性化页面只转交业务值与回调，`ThemeSettingsContent` 组合标准组件并保留主题 DMI 接线。它是主题业务组合，不是其他容器的公共效果来源。
7. **DMI 泛化〔暂缓〕：** 主题数据、菜单预算、返回预览与滚动条热区保留现有契约。本轮不宣称已经提供任意数据的通用 DMI，也不重写颜色滑块。
8. **独立开关与样式变体〔未扩展〕：** 当前未新增每实例涟漪弹簧参数、独立按压胶囊化开关或全部样式的运行时配置对象；使用已公开参数。没有使用场景时不扩展 API。

## 六、公共内容与分组过渡契约

### 1. 参数来源与职责

- `constants/motion/ContainerMotion`：单阶段时长 300ms，`FastOutSlowIn` 曲线，内容入退场位移 12dp。
- `animation/common/ContentTransition<T>`：只插值状态；接收初始状态、插值函数与帧回调，不依赖 View、业务或其他组件。再次播放从当前帧继续，系统关闭动画或 animate=false 时直接应用目标。
- `kit/settings/ExpandableContent`：标准组件内部的可选内容槽位，管理自然高度测量、高度过渡、透明度、位移、裁剪与输入屏蔽。页面使用成品容器 API，不直接驱动该槽位。
- `animation/common/GroupShape`：消费上下边的外圆角比例，使用 `SettingsGroupDimensions` 中外圆角 16dp、内圆角 4dp；插值采用 `ContentTransition` 的公共时序。
- `SettingsGroupDimensions.GAP_DP = 4dp` 为组内间距。分组宿主按实际可见直接成员确定 Single/First/Middle/Last；嵌套业务组合由其组件内部协调，不由页面重复实现。

### 2. 内容测量与交互

- 内容按自然高度测量，槽位再按展开进度决定当前高度；不把动画中的高度反向作为内容的测量约束。展开期间子内容请求重新布局时，按新的自然高度计算当前占位。
- 内容入场由上方 12dp 偏移回到原位、透明度由 0 到 1；退场反向。收起后高度为 0；卡片内容区上间距也随进度归零。
- 过渡时裁切槽位可见范围，完成后允许内容外溢。DMI 浮动标签的特殊裁剪仍由主题组合组件处理，不作为普通内容槽位默认规则。
- 进度未达到完整展开或动画仍在运行时，槽位屏蔽内部触摸，并隐藏其无障碍后代；标题行与内容区分别处理交互。

### 3. 更新、中断与卸载

- **展开反向：** 从当前可见进度反向，不先跳回起点。
- **内容替换：** 展开状态下先用 300ms 退出旧内容，再用 300ms 进入新内容，两阶段不是同时进行；退出期间再次替换只保留最新目标。收起状态直接替换，不自动展开。
- **展开指令打断替换：** 安装最新目标内容，再按最新展开状态过渡。
- **卸载：** 取消执行对象与回调，应用最新确认的展开状态；待替换内容安装为最新目标，不残留旧内容的延迟动画。
- **分组变化：** 已布局的组件从当前圆角向新位置过渡；首次布局直接显示目标形态。跨内容切换的同步过渡由标准组合组件共享帧输入。

### 4. 使用与验证边界

页面应只调用 `setContent`、`setContentExpanded`、`replaceContent`、`setExpanded`、确认状态更新方法及分组宿主。不要同时手动修改内部槽位高度、透明度和位移，否则会与组件的状态管理冲突。

纯几何回归见 `PressGeometryTest`；设备组件回归见 `SettingsComponentsTest`、`SettingsExpansionTest` 及原有按压、无障碍、菜单测试。模拟器验证用于布局、状态、输入与动画执行，真实震动手感仍由实体设备体验确认。

### 5. 本轮验证记录（2026-11-14）

- Debug 应用和测试 APK 构建通过；234 项单元测试通过。
- API 37 手机和平板模拟器各通过 26 项完整组件回归；随后对空内容、连续替换及内容父关系释放进行针对性复测，通过。
- Lint 为 0 错误、34 条已有警告。按压点击由 `PressHaptics` 调用宿主 `performClick`，保留无障碍点击入口。
- 实际页面检查覆盖父级菜单、应用设置、应用信息、主题模式切换、菜单滚动和组合容器展开；截图及 AndroidRuntime 日志检查未发现功能回归。
- 另用修改前版本在同一平板模拟器执行相同展开／收起操作做帧统计对照。样本未显示明显帧耗时退化，但模拟器仍存在卡顿，不将本轮功能回归通过解释为稳定 60 FPS 或实体设备触觉验收。
