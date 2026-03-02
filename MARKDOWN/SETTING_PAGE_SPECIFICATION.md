## 设置页面规范 (SETTING_PAGE_SPECIFICATION)

### 1. 架构哲学 (ARCHITECTURAL_PHILOSOPHY)

- 原子化解耦：UI 呈现层与业务逻辑层彻底分离，通过协议驱动渲染。
- 零硬编码颜色：所有视觉元素必须通过 ThemeExtension 动态注入。
- 动效资产化：所有设置页特有动画逻辑均沉淀于 kit_setting/animations 引擎。

### 2. 目录与物理架构 (DIRECTORY_ARCHITECTURE)

```text
lib/ui/design_system/kit_setting/      # 设置原子套件库
├── setting_header.dart                # 动态态射 Header
├── setting_card.dart                  # 圆角卡片容器 (CARD_PROTOCOL)
├── setting_tile.dart                  # 一级菜单原子项
├── setting_menu.dart                  # 协议驱动选择器 (单选/分段)
└── animations/                        # 物理动效引擎
    ├── expansion_animator.dart        # 容器扩张驱动器
    └── fade_slide_engine.dart         # 元素注入时序控制

lib/ui/pages/settings/                 # 枢纽调度层
├── settings_page.dart                 # 路由枢纽：负责 Hero 衔接与层级对冲
└── categories/                        # 业务专页隔离区 (Single File Isolation)
```

### 3. 组件协议规范 (COMPONENT_PROTOCOLS)

- SettingCard (卡片协议)：
  - 固定参数：20.0px 圆角，0.15 透明度阴影。
  - 隔离要求：内容区作为 Slot 注入，卡片不感知其内部业务逻辑。
- SettingMenu (选择器协议)：
  - 输入：选项列表 (options) + 渲染标签 (labels) + 当前状态 (current)。
  - 输出：ValueChanged<T> 回调。
  - 视觉：中性灰色 (UiColors.grey100) 圆角背景，左侧注入引导图标。

### 4. 动效规程 (ANIMATION_LOYALTY)

- 核心插值 (Core Interpolation)：
  - Header 态射：扩展进度从 0.0 到 1.0 时，圆角从 20.0 线性过渡至 0.0，标题缩放 0.85 恢复至 1.0。
  - 层级对冲：进入二级页时，内容区域执行 40px 的垂直负向位移 (Upwards Motion)。
- 连续性约束 (Continuity)：
  - 必须通过 `category_title_$title` Hero 标签确保标题在逻辑切换中的物理连续性。
- 扩张收缩逻辑 (Expansion/Contraction)：
  - 使用 AnimatedSize + AnimatedSwitcher 联动。
  - 节奏：Interval(0.5, 1.0) 用于渐隐/浮现，确保容器高度变换后再展示内容。

### 5. 启动页偏好嵌套设计 (STARTUP_PREF_LOGIC)

- 层级拓扑：
  - 一级：[成绩同步] | [歌曲详情] | [回溯退出点(终点)]
  - 二级 (同步)：[舞萌] | [中二] | [回溯退出点(终点)]
  - 三级 (同步)：[水鱼] | [落雪] | [双模式]
- 缓存机制 (Caching Scheme)：
  - 存储格式：`Primary:Secondary:Tertiary` 复合字符串。
  - 区分策略：若路径包含 "Last" 关键字，则路由分流至状态监听器 (Active State Observer)。

### 6. 开发禁令 (RESTRICTED_ACTIONS)

- REJECT：禁止在 categories/ 目录外引用设置业务逻辑。
- NO：禁止在组件内声明 UiColors 的非语义色值。
- ONLY：ThemeExtension 是设置内唯一的 DI/Render 渲染路径。
