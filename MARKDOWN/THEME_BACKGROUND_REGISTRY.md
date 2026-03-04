## 核心痛点与架构目标

- 痛点：现有方案采用静态数组硬编码注册，破坏开闭原则。新增背景皮肤文件后，需手动修改全局配置字典并编写对应的导入语句，容易导致文件遗漏和编译断层。
- 目标：建立基于代码生成的全自动发现方案。实现新建文件即注册，免去任何手动 import 与中心化管理配置项，仅由 UI 端读取生成产物即可。

## 第一阶段：设计统一注解信标

- 机制：通过定义专属注解类作为编译期收集器的寻找锚点。
- 实施：维持并使用现存的 `@GameTheme()` 注解作为唯一标识。编译器 ONLY 通过扫描带有该注解的类节点来确定其是否属于主题注册队伍。

## 第二阶段：应用标记约定与基建隔绝

- 机制：所有的背景主题子类 ONLY 凭借挂载 `@GameTheme()` 注解从而被自动收集器所探查，彻底解除与全局注册表的紧耦合。
- 实施：新建皮肤类时，必须继承 `AppTheme` 基类，并在类声明前标注 `@GameTheme()`。
- 属性闭环：要求类的内部 MUST 通过覆写 `domain` (游戏分区)、`themeTitle` (UI展示名称)、`themeId` (唯一键值) 提供其身份信息。
- 规程：背景类内部 ONLY 处理名称输出、域标识声明以及覆写 `buildBackground` 提供界面构建材质。REJECT 在当前类内包揽路由映射或向上传递状态的越界职能。

## 第三阶段：部署生成器与抽象语法树扫描 (AST Pipeline)

- 机制：利用 Dart 原生 `build_runner` 与拦截器实现源文件抽象语法树分析。
- 扫描逻辑：搜集带有 `@GameTheme()` 标记的子类，并通过 AST 分析其 `domain` 属性值，自动将其归拨至 `universalThemes`、`maimaiThemes` 或 `chunithmThemes` 集合。
- 实施：撰写专项的自动分析脚本 (`_generator.dart`)，拦截主题节点，自动提取其源文件相对路径与类名。
- 产物：编译结束后，在相同位置合成 `theme_catalog.g.dart`。机器负责拼接全量 `import` 引用，并输出结构体 `Map<String, AppTheme> allThemesRegistry` 以及各游戏域专属 List。

## 第四阶段：UI 端运行时对接

- 机制：设置页面中的主题切换列表，或负责解析持久化配置信息的加载类，ONLY 读取编译期所生成的只读静态汇编产物。
- 规程：开发流程实现收敛，未来加入新的视觉皮肤 ONLY 需新建文件加上注解印章，运行脚本即可完成 UI 可选列表的覆盖上架。REJECT 对注册路由系统的日常性人工侵入。

---

## 5. 颜色语义规范与生成器执行路径

- 契约约束：UI 组件 ONLY 循 `Theme.of(context).extension<AppTheme>()` 动态挂载色槽，REJECT 任何组件内硬编码越权行为。现有框架由于前期已完善剥离，无需二次整顿。
- 色槽语义：
  - BASIC (`medium`)：覆盖主功能页按钮、标签激活文字、分页指示点、边栏面板底区。
  - LIGHT (`light`、`subtitleColor`)：主负责 Logo 水印半透层、辅层轻量高光字。
  - DARK (`dark`)：针对如 MusicData 内深白底弹框的正文承载色，极值封顶 #2D2D2D 严苛屏障。
- 步骤 1：挂载生成链节
  - 定点 `pubspec.yaml`，向 `dev_dependencies` 注入 `build_runner`、`source_gen` 基建。
- 步骤 2：肢解环状耦合 (信标外置)
  - 将 `class GameTheme` 从 `app_theme.dart` 内剥除并挪至极简源 `theme_annotation.dart`，彻底断绝 AST 生成期扫描引爆无限套娃关联。
- 步骤 3：造册器开发 (`theme_generator.dart`)
  - 继承 `GeneratorForAnnotation<GameTheme>`，探头深入各主题子文件夹。程序 ONLY 单向提取 `domain` 名与所在 `.dart` 相对物理位置，无情拼装注册用静态 `Map` 片段。
- 步骤 4：拆毁人工列阵 (`theme_catalog.dart`)
  - 粗暴斩断原有硬核手写 `List`。引入 `part 'theme_catalog.g.dart'`，委托预处理脚手架掌管全域组装。唯一外包接头依旧定格为 `findThemeById`，阻绝接口动荡。
- 步骤 5：无感过界
  - 所有展示页枚举 `theme_catalog` 取用链路直接切换到自动生成池，前端业务零痛觉穿梭新机制。
