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
│   ├── settings/              # 设置层级切换动画
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
│   └── navigation/            # 全局切页、覆盖层开关、返回事件分发
│
├── theme/                     # 按音游组织主题，主题标识采用 mai-circle、chu-xversex 等形式
│   ├── chu/                   # CHUNITHM 主题；每个主题一个 Kotlin 文件，仅提供 HTML 入口与主题色
│   ├── mai/                   # maimai 主题；每个主题一个 Kotlin 文件，仅提供 HTML 入口与主题色
│   └── ThemeRegistry.kt       # 注册入口：统一登记稳定标识、展示名（如 Circle）、所属音游与默认项
│
└── window/                    # 当前应用窗口信息与布局判断，不按设备类型猜测安全区
    ├── breakpoints/           # 根据实际可用空间判断布局区间，浮窗与分屏同样适用
    ├── insets/                # 分别提供状态栏、导航栏、开孔、手势、窗口标题栏与键盘边距
    └── metrics/               # 当前窗口宽高与比例，随旋转、浮窗及自由缩放更新
```
