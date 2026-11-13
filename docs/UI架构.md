以下目录树包含现有结构与后续规划。`animation/pageAnimation`、`kit/pageMusicData` 和
`kit/pageScoreSync` 目前属于规划；功能页仍使用占位实现，不能作为已交付组件归档。

```
assets/
├── background/                # HTML 背景及其 CSS、JS、图片
└── fonts/                     # 原生与 HTML 使用的字体资源

ui/
├── animation/                 # 动画执行、取消、反向与收尾
│   ├── chrome/                # 外围 UI 动画
│   ├── common/                # 通用组件动画
│   ├── pageAnimation/         # 功能页动画
│   │   ├── pageMusicData/     # 卡片吸附、滚动退场、详情展开与回收
│   │   └── pageScoreSync/     # 授权切换、难度选择与日志展开
│   ├── settings/              # 设置层级、主题菜单、分段形状与箭头动画
│   └── shell/                 # 跨模块过渡动画
│
├── chrome/                    # 外围 UI 及其组合选择，向 shell 提供挂载入口
│   ├── ChromeAssembly.kt      # 接收 window 判断结果，选择并组装外围 UI，管理内部联动，供 shell 挂载
│   ├── NavigationBar.kt       # 导航栏
│   ├── SideBar.kt             # 侧边栏
│   └── TitleBar.kt            # 标题栏
│
├── constants/                 # UI 固定参数，不持有状态、不操作 View
│   ├── dimensions/            # 标准间距、组件尺寸、圆角、描边宽度
│   ├── motion/                # 标准动画参数：时长、刚度、阻尼、缩放与位移，暂不细分
│   └── typography/            # 字号、行高、字间距及字体角色
│
├── kit/
│   ├── common/                # 跨页面复用：按钮、输入框、弹窗等
│   ├── settings/              # 设置专用组件、主题选择与容器菜单
│   ├── pageMusicData/         # 歌曲／成绩页组件
│   └── pageScoreSync/         # 传分页组件
│       ├── chu/               # 中二专属组件
│       └── mai/               # 舞萌专属组件
│
├── pages/                     # 按功能分类，共用页面骨架，局部允许音游专属实现
│   ├── pageMusicData/         # 歌曲／成绩列表、筛选与详情
│   └── pageScoreSync/         # 传分页统一组装，按音游接入局部组件
│
├── settings/                  # 仅设置首页与直属子页两级，不再嵌套更深层级
│   ├── SettingsAssembly.kt    # 设置整体组装与首页／子页切换
│   ├── SettingsHome.kt        # 设置首页
│   └── subPages/              # 全部直属子页平放，每个子页一个文件
│
├── shell/                     # 总组装、全局 UI 状态、模块之间的连接
│   ├── background/            # 背景 WebView 接入、通信与生命周期
│   ├── selection/             # 主题选择纯快照、控制器、恢复与偏好适配
│   └── navigation/            # 全局切页、覆盖层开关、返回事件分发
│
├── theme/                     # 按音游组织主题，初始标识为 mai-circle-plus、chu-xversex
│   ├── chu/                   # CHUNITHM 主题；每个主题一个 Kotlin 文件，仅提供 HTML 入口与主题色
│   ├── mai/                   # maimai 主题；每个主题一个 Kotlin 文件，仅提供 HTML 入口与主题色
│   └── ThemeRegistry.kt       # 注册入口：统一登记稳定标识、展示名（如 CiRCLE+）、所属音游与默认项
│
└── window/                    # 当前应用窗口信息与布局判断，不按设备类型猜测安全区
    ├── breakpoints/           # 根据实际可用空间判断布局区间，浮窗与分屏同样适用
    ├── insets/                # 分别提供状态栏、导航栏、开孔、手势、窗口标题栏与键盘边距
    └── metrics/               # 当前窗口宽高与比例，随旋转、浮窗及自由缩放更新
```

## 依赖方向与公开边界

当前产品规则（2026-11-09）：仅浅色模式；普通手机全屏竖屏，大屏保留旋转与窗口响应。window 仍按稳定可用窗口宽度输出 Compact/Medium/Expanded（600/840dp）；chrome 使用 Compact 的标题+抽屉、Medium/Expanded 共用的 Rail，不加底部导航、不按设备型号或折叠长宽比另建标题。Rail 模式隐藏系统状态栏和手机标题，Compact 恢复白色系统状态区、深色图标；系统方向请求与系统栏执行归平台层，不属于 window 的事实采样。方向策略不能单凭 Compact 窗口判定手机并锁住大屏分屏。

抽屉的打开起手带为应用交互区域物理左侧 22%，手势仲裁归 chrome 的抽屉宿主，不进入 Activity、主题或功能页；背景仍通过公开取消协议归位。汉堡按钮的后续定制与用户已有页面按钮分开，不能跨组件重置按压属性。具体阶段交付以 [迁移总览](UI壳迁移/README.md) 和 [06 细则](迁移具体内容/06-标题栏.md) 为准。

全局视觉约束：**取消所有 UI 阴影**。标题、按钮、抽屉、导航胶囊、Rail、卡片、弹窗和后续页面组件均不得保留静态、默认或状态动画产生的投影；系统 elevation/translationZ 投影、兼容阴影、自绘模糊/渐变投影、文字阴影都在范围内。各组件负责关闭自身投影，不让 shell 遍历子 View 或增加全局阴影管理器。层级靠正确挂载顺序与明确覆盖关系保证，保留模态遮罩和触摸隔离。旧参数/资源在对应阶段核对引用后移除，不再为保真迁回阴影。

07 的抽屉具体采用 chrome 共同父容器与公开 ViewDragHelper：原四层在保持窗口原点的内容场景内，抽屉面板与遮罩由 chrome 自己拥有，父容器正常截获使旧目标收到 CANCEL。禁止反射修改 DrawerLayout、Activity 复制触摸事件或捕获背景内部 View。抽屉开合/进度是组件临时呈现，不进入导航状态或 UI 偏好；shell 只组合只读抽屉快照和 settingsOpen，统一决定底层输入、返回与模态焦点隔离。具体交接见 [07 细则](迁移具体内容/07-窄屏抽屉与导航.md)。

07 保留原有独立胶囊外观与透明面板，使用普通 View 组合，不为此引 Material 或切平台主题。模态遮罩使用用户要求的左深右浅、较低不透明度渐变，它属于背景隔离视觉，不属于已取消的组件投影。渐变颜色和开合进度由 chrome 自己绘制；透明度不得决定输入许可，右端透明时仍不能点穿底层页面。间距及初始颜色参数集中见 07 细则。

以下箭头表示允许的源码依赖，不表示必须为每条关系增加接口、框架或 Gradle 模块。未列出的跨职责依赖应先说明用途并修订本约定；不能因文件放进了新目录就认为完成了解耦。

- `MainActivity -> shell / window 的平台观测入口、纯断点边界 / constants`：平台配置、挂载、生命周期、系统回调和系统栏意图执行。window 依赖用于连接观测入口并转发生命周期、快照，方向策略可复用纯边界但不代替 chrome 分类；constants 仅读取平台配置所需固定参数。平台方向辅助允许使用现有 AndroidX 的最大窗口度量判断是否释放手机竖屏限制，避免展开后被兼容窄窗口误锁；此度量不得作为 WindowSnapshot、chrome 或内容几何的尺寸来源。Activity 不计算内容几何，不负责页面编排或业务流程。
- `shell` 组装层 `-> chrome / pages / settings / shell/navigation / shell/background / window / theme / animation/shell / kit/common`：只连接公开入口，不访问子模块内部 View 或业务实现。kit/common 仅用于壳直接创建的占位或展示节点所需的通用渲染工具；具体功能组件仍由 pages、settings、chrome 各自组装。
- `shell/navigation` 是导航状态与纯导航契约的独立边界，不依赖 chrome、pages、settings 或 shell 组装实现。`shell/background` 不依赖这些模块，只负责背景运行能力。
- `shell/background -> theme 的纯描述 / window 的只读数据 / constants / animation/shell`，以及 Android/WebKit/Pager 运行库；偏好策略和音游选择提交不放入 WebView Loader。
- `chrome -> kit/common / animation/chrome / constants / theme`，可使用 window 的只读数据类型，以及 navigation、background 暴露的纯展示类型。不得借此取得状态写入口、Pager、WebView 或 shell 实例；事件通过构造时传入的回调返回。
- `pages / settings -> 对应 kit / animation / constants / theme`，可消费传入的窗口、导航与背景快照；settings 可消费 `shell/selection/GameSelectionState` 的纯确认快照，不得引用其控制器、偏好适配器或主壳实例。页面状态持有者可以使用对应业务功能的公开入口；View 和组件只接收展示数据与事件回调。
- `kit/功能目录 -> kit/common / 对应 animation / constants / theme`；`kit/common -> animation/common / constants / theme`。组件不引用 pages、settings、chrome、shell 或业务实现，也不承担导航、授权、存储和网络请求。
- `animation -> constants`，按需消费纯数据和传入的 View/属性操作入口；各专属动画可复用 `animation/common`，common 不反向依赖专属动画。不得反向导入调用方的具体组件、状态容器或业务实现。动画不提交导航、音游或授权状态。
- `window -> Android 窗口 API、其内部纯数据/分类规则`；不依赖 theme、chrome、shell、页面或业务模块。窗口只报告事实，不执行导航或系统栏显示策略。
- `theme` 只包含主题描述和 Registry，不依赖其他 UI 运行模块、Context、View、存储或业务实现。`constants` 只含固定参数与纯描述，不读取资源或持有运行状态。

纯契约放在实际能力的公开入口旁，以独立纯类型文件声明，不引用所属能力的实现类；消费纯类型不代表可以依赖运行实现。例如 chrome 可以引用 `shell/navigation` 的纯 destination 类型，但不能引用 `MainShell`；这不允许 navigation 反向引用 chrome。不得为了共享一个类型导入整个宿主，也不新增万能 bridge、全局 service locator 或 ThemeExtension。

业务接线遵守 [业务架构](业务架构.md)：shell 接收页面装配入口，不接收再层层转交 TokenValidator、TokenStore、HTTP 客户端等业务依赖。页面业务入口由页面装配及依赖注入边界提供。唯一基础存储例外是 shell 的 UI 偏好适配器使用 `storage` 的最小偏好入口；它不处理凭据或业务数据。

## 状态、事件与恢复

- `shell/navigation` 唯一修改 destination 和覆盖层开关；chrome、页面只发送意图。设置打开时显示设置标题、主按钮执行返回，窄屏抽屉关闭并锁定；关闭后恢复功能呈现。设置中选择功能项，一次状态更新完成关闭覆盖层并切换 destination；普通返回只关闭覆盖层，不维护第二份目的地副本。
- chrome 可以持有抽屉拖动、测量和控件动画等局部瞬态；它们不成为第二份 destination/覆盖层状态。窗口切换后按导航快照重新派生按钮动作和抽屉许可。
- shell 的音游/主题状态持有者唯一修改已选音游和各音游主题；background 将 Pager 事件转换为选择意图和瞬时快照。快照明确已选音游、Mai 到 Chu 的归一化进度及 `Idle / Dragging / Settling` 阶段；首次订阅必须收到当前快照。
- 音游选择在 Pager 的选中事件提交，拖动帧不提交选择；主题切换校验所属音游。暂停、取消或布局重组终止当前手势时，背景与消费者归位到最近已提交音游的 Idle 状态，旧回调不能覆盖新一轮状态。
- 持久偏好保存当前音游和各音游主题；导航/覆盖层等只按 UI 恢复协议保存。恢复优先使用合法的保存状态，其次持久偏好，再使用 Registry 默认值；未知或不属于该音游的主题回退到该音游默认项。恢复时不把默认值提前写回偏好。
- 拖动进度、Animator、临时 View 和窗口尺寸不持久保存；重建时重新观测窗口，背景进度归位。View 生命周期与稳定状态分开，chrome 重组不得重建全部背景或重置页面状态。
- 每个可动画属性在同一时刻只有一个执行者。进入设置、切页等交互接管属性前，取消冲突动画及延迟回调，再按已提交状态呈现；动画结束只清理视觉资源，不决定功能状态。

## 主题选择边界

主题选择的具体行为见 [主题选择与背景来源](主题选择.md)。`shell/selection` 的纯状态与恢复规则不依赖设置页、背景宿主或 View；仅 `UiPreferencesAdapter` 可将稳定快照编码给 storage 的基础偏好接口。读记录允许缺失字段，写入必须提供完整选择快照及版本，包括模式、合并主题、无主题种子色、背景跟色、壁纸取色及主副色，不能用默认空实现忽略新增字段。

个性化页只保存已确认的展示快照；点击按钮或主题项只发意图，不乐观复制修改领域选择。`MainShell` 对公开选择入口及背景确认统一回传快照；`SettingsAssembly` 在意图处理后重新读取确认值，拒绝选择也不能遗留本地选中状态。

`kit/settings` 拥有菜单实例、触摸指针、布局与触觉任务，`animation/settings` 拥有对应动画对象、运动交接和取消收尾。菜单几何与展开门槛为纯计算，可留在组件边界；固定几何与运动参数分别进入 constants 的 dimensions/motion。动画不得通过具体组件的内部字段回写选择。背景黑幕只由 `animation/shell` 驱动视觉时序，shell 通过回调接线换源及输入禁用，仍是稳定选择唯一所有者。

内嵌主题面板属于设置组件的临时呈现，不进入导航状态或偏好。系统返回通过设置公开入口锁定 `SettingsMenu` 预测目标，取消恢复面板、确认只关闭面板；标题栏返回独立执行设置层级导航。主壳不取得菜单 View 或滚动位置，页面卸载负责清理这些局部状态。

## 几何与验收约束

所有布局输入统一使用当前应用窗口坐标与 px，断点分类时再按当前 density 换算 dp。系统安全区按重叠区域合并：同一边的状态栏、开孔、窗口标题栏不逐项相加。systemGestures 保留为手势竞争信息，不直接当作所有层的强制 padding；IME 单独影响可交互内容，不改变背景尺寸或导航形态。

chrome 上报的是**不含系统安全区的应用额外占用**。标题总容器可包含系统顶区，但上报值只包含应用标题行；Rail 上报自身宽度。shell 先取得系统安全矩形，再扣应用占用，统一输出背景/功能/覆盖层的基础内容矩形；页面不得再次扣同一份边距。键盘避让在基础矩形之上仅应用于需要避让的内容。

shell 缓存当前有效窗口快照和应用 chrome 占用，窗口变化与标题实测占用变化都调用同一几何重算入口；不能依赖已去重的窗口回调碰巧再次发生才调整内容。相同占用去重，只有背景实际视口矩形改变才触发背景归位；主题换色或 IME 独立变化不能截断背景正常 settling。

每步验收检查实际 import、构造参数、状态写入口、订阅解绑和恢复路径；禁止以“目录已拆分”替代边界检查。几何验证使用显式输入，例如系统顶区 24px、标题行 72px 时内容顶边为 96px；已有标题容器总高 96px 不能再次加 24px。动画验证包含音游回弹中打开设置、设置退出时横滑及转场中跨断点。
