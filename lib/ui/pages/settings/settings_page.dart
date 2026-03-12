import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../application/shared/game_provider.dart';
import '../../design_system/constants/colors.dart';
import '../../design_system/kit_shared/kit_staggered_entrance.dart';
import '../../../application/shared/navigation_provider.dart';
import '../../design_system/kit_setting/setting_header.dart';
import '../../design_system/kit_setting/setting_tile.dart';
import 'categories/app_settings_page.dart';
import 'categories/personalization_page.dart';
import 'categories/sync_service_page.dart';
import '../../design_system/constants/responsive_layout_scope.dart';
import '../../design_system/constants/sizes.dart';
import '../../design_system/theme/core/app_theme.dart';
import '../../design_system/theme/universal_theme/star_trails.dart';
import 'categories/about_page.dart';

/// 设置模块门面容器：Overriding Layer (v4.0)
/// 已重构：移除旧的分页逻辑，采用简洁的垂直卡片式列表。
class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage>
    with TickerProviderStateMixin {
  late NavigationProvider _nav;
  late AnimationController _fadeController;
  late Animation<double> _fadeAnimation;

  // 扩张动效状态
  int? _activeCategoryIndex;
  late AnimationController _expansionController;
  late Animation<double> _expansionAnimation;

  // 即时图层变轨快照 (Layer Swap)
  ui.Image? _currentSnapshot;
  ui.Image? _oldSnapshot;
  late AnimationController _swapController;
  late Animation<double> _swapAnimation;
  int _lastThemeHash = 0;

  final List<({IconData icon, String title, Color color, Widget page})>
  categories = [
    (
      icon: Icons.sync,
      title: "成绩同步设置",
      color: Colors.green,
      page: const SyncServicePage(themeColor: Colors.green),
    ),
    (
      icon: Icons.settings,
      title: "应用设置",
      color: Colors.blue,
      page: const AppSettingsPage(themeColor: Colors.blue),
    ),
    (
      icon: Icons.palette,
      title: "个性化设置",
      color: Colors.purpleAccent,
      page: const PersonalizationPage(themeColor: Colors.purpleAccent),
    ),
    (
      icon: Icons.info_outline,
      color: Colors.grey,
      title: "应用信息",
      page: const AboutPage(themeColor: Colors.grey),
    ),
  ];

  @override
  void initState() {
    super.initState();
    // 与右侧二级内容淡出一致：500ms + easeInOutQuart，返回效果统一
    _fadeController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 500),
    );
    _fadeAnimation = CurvedAnimation(
      parent: _fadeController,
      curve: Curves.easeInOutQuart,
    );

    _expansionController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 500),
    );
    _expansionAnimation = CurvedAnimation(
      parent: _expansionController,
      curve: Curves.easeInOutQuart,
    );

    _swapController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 300),
    );
    _swapAnimation = CurvedAnimation(
      parent: _swapController,
      curve: Curves.easeOutCubic,
    );

    _fadeController.forward();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _nav = context.read<NavigationProvider>();

    // 取消此处对 _nav.bgSnapshot 盲目的吃入并缓存行为，
    // 因为这会干扰真正的新摄制流程状态，只应当从顶栈快照获取一次或让隔离层自动处理。
    if (_currentSnapshot == null && _nav.bgSnapshot != null) {
      _currentSnapshot = _nav.bgSnapshot;
    }

    final gp = context.watch<GameProvider>();
    final newHash = Object.hash(
      gp.isThemeGlobal,
      gp.activeSkinId,
      gp.maiSkinId,
      gp.chuSkinId,
    );

    if (_lastThemeHash == 0) {
      _lastThemeHash = newHash;
    } else if (_lastThemeHash != newHash) {
      _lastThemeHash = newHash;
      _triggerLayerSwap();
    }
  }

  void _triggerLayerSwap() async {
    // [时差修补] 延长 150ms 等待至 200ms
    // 强制挂起快照捕捉，为了给予底层所有的 AnimatedContainer, AnimatedDefaultTextStyle
    // 等隐式动画组件足够的 150ms-200ms 退场和渲染到全新颜色的时间，避免截取出带有旧色残影的断层假象。
    await Future.delayed(const Duration(milliseconds: 200));
    if (!mounted) return;

    final newImg = await _nav.captureTask?.call();
    if (newImg != null && mounted) {
      _nav.registerTempSnapshot(newImg);
      setState(() {
        _oldSnapshot = _currentSnapshot;
        _currentSnapshot = newImg;
      });
      _swapController.forward(from: 0.0);
    }
  }

  @override
  void dispose() {
    _fadeController.dispose();
    _expansionController.dispose();
    _swapController.dispose();
    // 采用缓存引用清理背景快照，规避 context 停用异常 (Memory GC)
    _nav.clearBgSnapshot();
    super.dispose();
  }

  Widget _buildBlurEngine(ui.Image image) {
    return RepaintBoundary(
      child: ImageFiltered(
        imageFilter: ui.ImageFilter.blur(
          sigmaX: 15.0,
          sigmaY: 15.0,
          tileMode: TileMode.mirror,
        ),
        child: RawImage(
          image: image,
          fit: BoxFit.cover,
          filterQuality: FilterQuality.low,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final topPadding = MediaQuery.of(context).padding.top;
    final skinExtension =
        Theme.of(context).extension<AppTheme>() ?? const StarTrailsTheme();
    final scope = ResponsiveLayoutScope.maybeOf(context);
    final width = scope?.primaryPaneWidth ?? MediaQuery.sizeOf(context).width;
    final isLargeScreen = width > 600;

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, result) {
        if (didPop) return;
        _handleBack();
      },
      child: Theme(
        data: Theme.of(context).copyWith(extensions: [skinExtension]),
        child: Material(
          color: Colors.transparent,
          child: Stack(
            children: [
              // 1. 物理隔离快照背景 (Snapshot Isolation Layer)
              Positioned.fill(
                child: Consumer<NavigationProvider>(
                  builder: (context, nav, _) {
                    return GestureDetector(
                      onTap: _handleBack,
                      child: AnimatedBuilder(
                        animation: _fadeAnimation,
                        builder: (context, _) => Opacity(
                          opacity: _fadeAnimation.value,
                          child: Stack(
                            fit: StackFit.expand,
                            children: [
                              // 1. 底层：新摄制的背景
                              if (_currentSnapshot != null)
                                _buildBlurEngine(_currentSnapshot!),

                              // 2. 表层：被淘汰的老背景，正在做 FadeOut (1 -> 0)
                              if (_oldSnapshot != null)
                                AnimatedBuilder(
                                  animation: _swapAnimation,
                                  builder: (context, _) => Opacity(
                                    opacity: 1.0 - _swapAnimation.value,
                                    child: _buildBlurEngine(_oldSnapshot!),
                                  ),
                                ),

                              // 3. Fallback 或者垫底灰度
                              if (_currentSnapshot == null &&
                                  _oldSnapshot == null)
                                Container(
                                  color: UiColors.white.withValues(alpha: 0.25),
                                ),
                            ],
                          ),
                        ),
                      ),
                    );
                  },
                ),
              ),

              // 2. 核心内容区 (分段响应动效)
              AnimatedBuilder(
                animation: Listenable.merge([
                  _fadeAnimation,
                  _expansionAnimation,
                ]),
                builder: (context, _) {
                  // 平板模式：顶部不播扩张动画，仅保留「返回首页」常驻；手机模式：保留原有 Phase A/B 动画
                  // 扁平层级：任意返回均直接回功能页，与右侧二级淡出效果一致
                  final headerChild = isLargeScreen
                      ? SettingHeader(
                          title: '返回首页',
                          icon: Icons.home_outlined,
                          iconColor: Colors.transparent,
                          onBack: _handleBack,
                          isSubPage: false,
                        )
                      : SizedBox(
                          height: topPadding + 54,
                          child: Stack(
                            children: [
                              ClipRect(
                                child: Align(
                                  alignment: Alignment.topCenter,
                                  heightFactor: 1 - _expansionAnimation.value,
                                  child: Opacity(
                                    opacity:
                                        (1 - _expansionAnimation.value) *
                                        _fadeAnimation.value,
                                    child: SettingHeader(
                                      title: '返回首页',
                                      icon: Icons.home_outlined,
                                      iconColor: Colors.transparent,
                                      onBack: _handleBack,
                                      isSubPage: false,
                                    ),
                                  ),
                                ),
                              ),
                              if (_activeCategoryIndex != null)
                                ClipRect(
                                  child: Align(
                                    alignment: Alignment.topCenter,
                                    heightFactor: _expansionAnimation.value,
                                    child: SettingHeader(
                                      title: categories[_activeCategoryIndex!]
                                          .title,
                                      icon: categories[_activeCategoryIndex!]
                                          .icon,
                                      iconColor:
                                          categories[_activeCategoryIndex!]
                                              .color,
                                      expansionProgress:
                                          _expansionAnimation.value,
                                      onBack: _handleBack,
                                    ),
                                  ),
                                ),
                            ],
                          ),
                        );

                  return Opacity(
                    opacity: _fadeAnimation.value,
                    child: Column(
                      children: [
                        ClipRect(
                          child: Align(
                            alignment: Alignment.topCenter,
                            heightFactor: _fadeAnimation.value,
                            child: isLargeScreen
                                ? SizedBox(
                                    height: topPadding + 54,
                                    child: headerChild,
                                  )
                                : headerChild,
                          ),
                        ),
                        Expanded(
                          child: Transform.translate(
                            offset: Offset(0, 40 * (1 - _fadeAnimation.value)),
                            child: LayoutBuilder(
                              builder: (context, constraints) {
                                final scope =
                                    ResponsiveLayoutScope.maybeOf(context);
                                final width = scope?.primaryPaneWidth ??
                                    MediaQuery.sizeOf(context).width;
                                final isLarge = width > 600;
                                if (isLarge) return _buildLargeScreenContent();
                                return _buildPhoneContent();
                              },
                            ),
                          ),
                        ),
                      ],
                    ),
                  );
                },
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget? _cachedSubPage;

  /// 大屏：左右 5:11 分栏，中间间距为选项卡片间距 2 倍，正中 2px 白线（不贯穿，上下标准间距）
  Widget _buildLargeScreenContent() {
    const cardSpacing = UiSizes.spaceS; // 12，与 _buildMainList 中 bottom 一致
    const gapWidth = cardSpacing * 2; // 24
    const lineInset = UiSizes.spaceM; // 16，标准间距
    const lineWidth = 2.0;

    return Row(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // 左侧 5 份：分类列表（平板常驻；右侧无 padding，仅保留中间缝间距）
        Expanded(
          flex: 5,
          child: _buildMainList(forLargeScreen: true),
        ),
        // 中间：间距 + 不贯穿白线（上下各 lineInset）
        SizedBox(
          width: gapWidth,
          child: Column(
            children: [
              const SizedBox(height: lineInset),
              Expanded(
                child: Center(
                  child: Container(
                    width: lineWidth,
                    color: Colors.white,
                  ),
                ),
              ),
              const SizedBox(height: lineInset),
            ],
          ),
        ),
        // 右侧 11 份：二级页或空白（左移抵消二级页自身 horizontal margin，避免与中间缝叠加）
        Expanded(
          flex: 11,
          child: _activeCategoryIndex != null
              ? RepaintBoundary(
                  child: Opacity(
                    opacity: _expansionAnimation.value,
                    child: Transform.translate(
                      offset: Offset(
                        -UiSizes.getHorizontalMargin(context),
                        40 * (1 - _expansionAnimation.value),
                      ),
                      child:
                          _cachedSubPage ?? const SizedBox.shrink(),
                    ),
                  ),
                )
              : const SizedBox.shrink(),
        ),
      ],
    );
  }

  /// 手机：保持原有整屏 Stack 切换
  Widget _buildPhoneContent() {
    return Stack(
      children: [
        Opacity(
          opacity:
              (1 - _expansionAnimation.value) * _fadeAnimation.value,
          child: IgnorePointer(
            ignoring: _activeCategoryIndex != null,
            child: _buildMainList(),
          ),
        ),
        if (_activeCategoryIndex != null)
          RepaintBoundary(
            child: Opacity(
              opacity: _expansionAnimation.value,
              child: Transform.translate(
                offset: Offset(
                  0,
                  40 * (1 - _expansionAnimation.value),
                ),
                child:
                    _cachedSubPage ?? const SizedBox.shrink(),
              ),
            ),
          ),
      ],
    );
  }

  void _handleCategoryTap(int index) {
    setState(() {
      _activeCategoryIndex = index;
      _cachedSubPage = _buildThemedSubPage(context);
    });
    _expansionController.forward();
  }

  /// 物理隔离：预构建二级页，隔离 AnimatedBuilder 的高频重绘。
  Widget _buildThemedSubPage(BuildContext context) {
    if (_activeCategoryIndex == null) return const SizedBox.shrink();

    final cat = categories[_activeCategoryIndex!];
    final skin =
        Theme.of(context).extension<AppTheme>() ?? const StarTrailsTheme();

    return Theme(
      key: ValueKey('themed_page_${_activeCategoryIndex}'),
      data: Theme.of(
        context,
      ).copyWith(extensions: [skin.copyWith(basic: cat.color)]),
      child: cat.page,
    );
  }

  /// [forLargeScreen] 为 true 时右侧无 padding，避免与中间缝叠加成双倍间距。
  Widget _buildMainList({bool forLargeScreen = false}) {
    final padding = forLargeScreen
        ? const EdgeInsets.only(left: 16, top: 20, bottom: 20, right: 0)
        : const EdgeInsets.symmetric(horizontal: 16, vertical: 20);
    return SingleChildScrollView(
      padding: padding,
      child: Column(
        children: List.generate(categories.length, (index) {
          final cat = categories[index];
          return Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: KitStaggeredEntrance(
              index: index + 1,
              duration: const Duration(milliseconds: 300),
              delay: Duration(milliseconds: index * 40),
              child: SettingTile(
                icon: cat.icon,
                title: cat.title,
                iconColor: cat.color,
                onTap: () => _handleCategoryTap(index),
              ),
            ),
          );
        }),
      ),
    );
  }

  /// 扁平层级：任意选项内返回均直接回功能页，淡出与右侧二级内容一致（500ms easeInOutQuart）
  void _handleBack() {
    _fadeController.reverse().then((_) {
      if (mounted) {
        setState(() {
          _activeCategoryIndex = null;
          _cachedSubPage = null;
        });
        context.read<NavigationProvider>().closeSettings();
      }
    });
  }
}
