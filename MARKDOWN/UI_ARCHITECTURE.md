# UI 架构文档

## 目录结构

```
lib/ui/
│
├── design_system/                          # 设计系统（原材料与动效引擎）
│   │
│   ├── page_shell.dart                     # 基础容器：毛玻璃底板容器
│   │
│   ├── constants/                          # 原子常量定义
│   │   ├── sizes.dart                      # 间距、圆角、动画时长、自适应边距计算
│   │   └── assets.dart                     # 资源路径映射 (背景、Logo、难度图)
│   │
│   ├── kit_shared/                         # 通用原子与协议
│   │   ├── game_page_item.dart             # ✅ 协议：多游戏轮播页面定义
│   │   ├── kit_game_carousel.dart          # ✅ 引擎：统一动效轮播器 (视差/挤压/背景渐变)
│   │   ├── sticky_dot_indicator.dart       # 组件：粘性动力学分页指示器
│   │   └── confirm_button.dart             # 组件：统一缩放反馈确认按钮
│   │
│   ├── kit_score_sync/                     # 业务组件：成绩同步专项
│   │   ├── score_sync_card.dart            # 容器：支持弹性滚动的业务卡片
│   │   ├── score_sync_form.dart            # 复合：多模式 Token 输入与验证表单
│   │   ├── sync_log_panel.dart             # 复合：阶段化时序日志控制台
│   │   └── mai_dif_choice.dart             # 业务：舞萌/中二难度选择器
│   │
│   └── visual_skins/                       # 皮肤系统 (ThemeExtension 伪 DI)
│       ├── skin_extension.dart             # 接口：定义 primary/success/error 及背景渲染
│       └── implementations/                # 实现：各游戏具体视觉调性定义 (Maimai/Chunithm)
│
└── pages/                                  # 组装层
    │
    ├── score_sync/                         # 传分模块
    │   ├── score_sync_page.dart            # 组装：通过 Carousel 协议挂载各游戏页
    │   └── components/                     # 局部逻辑：Logo 包装器与业务流水线
    │
    └── settings/                           # 设置模块
        └── settings_page.dart              # 组装：基础表单页面
```

---

## 核心设计原则

### 1. 协议驱动 UI (Protocol-Driven UI)

- **KitGameCarousel**：将物理层动画（视差、缩放、旋转、渐变）与业务层内容完全解耦。
- **扩展性**：新增游戏功能 ONLY 需要向该协议注册一个新的 `GamePageItem`，无需修改任何动效代码。

### 2. 皮肤依赖注入 (ThemeExtension DI)

- **去硬编码**：严禁在 `kit` 层使用 `Colors.xxx` 或硬编码 Hex。
- **强制约束**：组件必须通过 `Theme.of(context).extension<SkinExtension>()` 消费颜色。
- **自动插值**：利用 `SkinExtension.lerp` 方法，背景与 UI 色彩随滑动进度自动完成平滑过渡。

### 3. 单向依赖方向

- **Pages** -> **Application** -> **Kit (Shared/Specific)** -> **Visual Skins**
- **Logic** <- **Application** -> **Kernel**
- **REJECT**：严禁 Page 层直接引用具体的皮肤实现类 (Implementations)，应通过 `GamePageItem` 协议层注入。
- **REJECT**：严禁 Logic 层引用 Flutter/UI 相关的类 (如 TextEditingController)，此类需求应在 Application 层处理。

### 4. 布局规程 (Layout Conventions)

- **由内向外撑开 (Internal Pushing Pattern)**：
  - **原理**：禁止在父容器外部通过 `height` 强制干预 UI 体积（除非是固定高度场景）。容器高度的增长应由内部业务组件通过千斤顶效应 (Jacking Effect) 产生物理占位，利用 Flutter 的 `WrapContent` 天性将父容器自然“顶”开。
- **有限约束安全准则 (Finite Constraint Safety)**：
  - **原理**：Flutter 禁止在 `Finite`（有限）与 `Unbounded/Infinity`（无限）约束之间进行动画插值，否则会触发渲染断言崩溃。
  - **规程**：所有执行容器高度动画的起点与终点必须是确切的 Finite 数值。终点高度应通过 `MediaQuery` 与 `sizes.dart` 规程预计算出绝对像素值，**REJECT** 在 `AnimatedContainer` 等动画组件中使用 `double.infinity` 或依赖 `Flexible/Expanded` 隐性提供的无限边界作为动画目标。
- **几何精算与自适应 (Adaptive Geometry)**：
  - **原则**：严禁使用魔数硬编码组件高度。
  - **规程**：组件的目标视口高度、边缘间距必须基于物理设备规格实时计算。公式：`目标高度 = 屏幕总高 - 顶部安全偏移 - 静态组件占用 - 底部原子间距(12px)`。
- **物理与视觉异步 (Physics-Visual Decoupling)**：
  - **原则**：体积变化优先，内容渲染置后。
  - **规程**：在执行大规模高度扩张时，必须将其分解为：**阶段 A（物理占位容器扩张）** 与 **阶段 B（内部文字/复杂内容淡入挂载）**。确保在体积动画定型前，不进行引发 `RenderFlex` 报错的高开销排版重绘。
- **强语义边缘界限锁死 (Semantic Spacing Lockdown)**：
  - **原则**：严禁滥用物理宽度相同的间距变量。
  - **规程**：所有的 `12.0` 标准间距被锁死且划分为三个独立语义：
    - `spaceS`：**ONLY** 用于原子级或最小控制单元间的填充与外包。
    - `atomicComponentGap`：**ONLY** 用于**垂直方向**各业务封装块（如 Logo、卡片、分隔线、控制面板）直接的占位隔离。
    - `cardContentPadding`：**ONLY** 用于**水平方向**最外层主卡片（如 `ScoreSyncCard`）限制其囊括子元素左右扩张界限的安全锁进。**REJECT** 将这三者合并为统一的全局变量使用。

---

## 动效引擎协议 (KitGameCarousel)

### GamePageItem 定义

用于描述参与轮播的每一个单元：

```dart
class GamePageItem {
  final SkinExtension skin;    // 该页面的皮肤实现
  final Widget content;        // 该页面的业务内容
  final String title;          // 标识符
}
```

### 渲染流程

1. **背景层**：根据当前 `pageIndex` 和 `nextIndex` 自动执行 `Opacity` 叠加，实现跨背景渐变。
2. **内容层**：基于 `abs(page - index)` 计算缩放比例 (Scale) 与视差偏移 (Parallax Offset)，产生物理挤压感。
3. **指示器层**：计算两页之间的 `lerp(skinA, skinB, t)`，驱动指示器颜色平滑流变。

---

## 颜色管理规范 (Semantic Tokens)

### 1. 核心调色盘

每个皮肤必须提供以下语义化 Token：

- **light**：用于浅层叠加或渐变起始。
- **medium**：主色调，用于按钮、高亮指示。
- **dark**：深色调，用于边框、投影或渐变终止。

### 2. 交互状态

- **Disabled**：所有组件的不可用状态必须统一叠加 60% 透明度的纯黑遮罩 (`UiColors.disabledMask`)，禁止随意使用灰色 (`Colors.grey`) 降低透明度或完全脱离主题底色。
- **Feedback**：点击变暗效果应由 `lerp(baseColor, UiColors.black, factor)` 生成，确保色相调性一致。

---

## 开发约束

- **命名规范**：所有设计系统组件必须以 `Kit` 或该业务包的前缀命名。
- **逻辑隔离**：`kit` 层组件不得持有 `Provider` 业务状态，只接受原始数据与回调。
- **自适应**：所有垂直边距必须通过 `UiSizes.getTopMarginWithSafeArea` 动态获取，以兼容不同规格的 Notch/Dynamic Island。
- **统一交互组件**：所有类似确认、取消等基础交互按钮组件，必须统一从 `kit_shared/confirm_button.dart` 等原子路径引入，**REJECT** 在业务包内散装实现 `GestureDetector` + `Container` 容器。
- **全局物理反馈**：所有自定义可点击交互组件 (如圆圈、纯图按钮的缩放反馈)，必须遵循“按下即时收缩、松开即时回正”的物理反馈。此动效 **ONLY** 允许通过包裹 `kit_shared/kit_bounce_scaler.dart` 实现包装。**REJECT** 各业务线独立实现 `ScaleTransition` 或手势识别逻辑。
- **层级动效规律**：所有涉及到层级交替（展现与销毁）的滑入滑出或淡入淡出动效，必须从 `kit_shared/kit_animation_engine.dart` 摄取标准的曲线与时长（如展出使用 600ms `easeOutQuart`），**REJECT** 使用魔数硬编码的 Curve。
- **层级动效约束**：所有涉及到层级交替的淡入淡出效果，**必须 ONLY** 在透明度 (Opacity) 层面进行调整。**REJECT** 因高度、边距变化或动态大小 (`AnimatedSize`、`AnimatedCrossFade` 挤压等) 引起的 DOM 结构与物理容器体积跳变。

---

## 状态隔离与扩展规程 (State Isolation & Extension Protocol)

### 1. 核心逻辑：组合隔离机制 (Composite Isolation)

通过 "Element 内存键值强制重建 (UI)" + "Stateful 局部私有化托管 (Page)" + "Provider 维度鉴权标记 (Logic)" 三维一体的分布策略来分别管理多页面状态。

### 2. UI 渲染层：ValueKey 气隙隔离 (Element Tree Gap)

- **机制**：利用 Flutter 原生的 `Widget.canUpdate` 判断条件，通过注入硬性 Key 值，强行阻断底层 Element 树对相同组件的内存跨界复用。由底层引擎自动隔离所有隐式状态（例如：表单焦点、文本遗留、折叠动画、日志区残影）。
- **扩展规程**：未来向轮播器注册任何新游戏页面并调用公共组件 (如 `ScoreSyncAssembly`, `ScoreSyncForm`) 时，**ONLY** 绑定包含页面平台专属标识（如 `gameType`）的衍生特征键。
- **标本**：`ScoreSyncAssembly(key: ValueKey('ScoreSyncAssembly_$gameType'), ...)`

### 3. Page 业务装配层：宿主状态自持 (Host State Self-Hosting)

- **机制**：外层轮播器所装载的子项，皆定义为独立的 `StatefulWidget` 代理舱（如 `MaiSyncPage`、`ChuSyncPage`）。
- **扩展规程**：类似 `_transferMode` 这种只决定当前视图渲染选项、不进入数据库和无长驻生命周期的参数，**ONLY** 交由具体衍生页面的 `State` 闭环维系，不参与全局状态树共享。

### 4. Application 全局层：维度标签鉴别 (Dimension Tagging Filter)

- **机制**：鉴于 `TransferProvider` 是全局或父级单例，网络请求与日志广播会跨页面涌动。依赖状态参数 `trackingGameType` 实施接收者身份过滤。
- **扩展规程**：任何新拓展的页面在触发长生命周期或跨栏异步调度时，必须将当前唯一标志 (如 `Osu` -> `gameType: 2`) 作为指纹传输。日志监控或遮罩等业务组件，依靠 `provider.trackingGameType == currentGameType` 的二元等式开合闸门。

### 5. 管家与衣柜的职能隔离 (Provider vs Pseudo-DI)

- **职能界限定义**：
  - **衣柜 (ThemeExtension DI)**：**被动的打扮工具**。ONLY 负责在视图层被拿取时，提供当前游戏域的专属视觉设定（颜色系、风格等）。**REJECT** 让衣柜感知业务进度、网络状态或执行逻辑计算。
  - **管家 (Application Provider)**：**只认数据的心智盲区办事员**。ONLY 负责向底层下发跑腿任务，并向全域广播纯粹的进度数据或异常文案。**REJECT** 在 Provider 内出现任何关于颜色 Hex、UI 弹窗样式、具体字号的定义或判断。
- **短期记忆隔离机制 (Short-term Memory Lockdown)**：
  - **原理**：缓解“中枢集权隐患”，防止管家变成拥挤的 UI 杂物间。
  - **规程**：组件层面纯粹的视效动作（如：日志面板是否展开、难度选项的悬停态、表单未确认前的临时输入），**ONLY** 允许驻留在组件自身的 `State` 或局部沙盒中闭环消化。
  - **REJECT**：严禁将任何“未敲定”的临时 UI 变化跨级上供至 `Provider`，`Provider` ONLY 应在点击“最终执行”的按键时被动接收打包参数。

---

**文档版本**: v2.0 (Protocol Update)  
**最后更新**: 2026-02-26  
**维护者**: Antigravity Assistant Team
