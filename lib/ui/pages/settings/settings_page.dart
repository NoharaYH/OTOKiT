import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../design_system/constants/colors.dart';
import '../../design_system/constants/animations.dart';
import '../../design_system/kit_shared/kit_staggered_entrance.dart';
import '../../../application/shared/navigation_provider.dart';
import '../../design_system/kit_setting/setting_header.dart';
import '../../design_system/kit_setting/setting_tile.dart';
import 'categories/app_settings_page.dart';
import 'categories/personalization_page.dart';
import 'categories/sync_service_page.dart';
import '../../design_system/visual_skins/implementations/defaut_skin/star_background.dart';
import '../../design_system/visual_skins/skin_extension.dart';
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
  late AnimationController _fadeController;
  late Animation<double> _fadeAnimation;

  // 扩张动效状态
  int? _activeCategoryIndex;
  late AnimationController _expansionController;
  late Animation<double> _expansionAnimation;

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
    _fadeController = AnimationController(
      vsync: this,
      duration: UiAnimations.standard,
    );
    _fadeAnimation = CurvedAnimation(
      parent: _fadeController,
      curve: Curves.easeOut,
    );

    _expansionController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 500),
    );
    _expansionAnimation = CurvedAnimation(
      parent: _expansionController,
      curve: Curves.easeInOutQuart,
    );

    _fadeController.forward();
  }

  @override
  void dispose() {
    _fadeController.dispose();
    _expansionController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final topPadding = MediaQuery.of(context).padding.top;
    final skinExtension =
        Theme.of(context).extension<SkinExtension>() ??
        const StarBackgroundSkin();

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
              // 1. 全局背景拦截 (仅随 _fadeAnimation 变化)
              Positioned.fill(
                child: GestureDetector(
                  onTap: () => _activeCategoryIndex == null
                      ? _handleBack()
                      : _handleCategoryBack(),
                  child: AnimatedBuilder(
                    animation: _fadeAnimation,
                    builder: (context, _) => Opacity(
                      opacity: _fadeAnimation.value,
                      child: RepaintBoundary(
                        child: BackdropFilter(
                          filter: ImageFilter.blur(sigmaX: 15.0, sigmaY: 15.0),
                          child: Container(
                            color: UiColors.white.withValues(alpha: 0.25),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),

              // 2. 核心内容区 (分段响应动效)
              AnimatedBuilder(
                animation: Listenable.merge([
                  _fadeAnimation,
                  _expansionAnimation,
                ]),
                builder: (context, _) => Column(
                  children: [
                    // 动态 Header 区域 - 向下滑入 (Slide Down)
                    ClipRect(
                      child: Align(
                        alignment: Alignment.topCenter,
                        heightFactor: _fadeAnimation.value,
                        child: SizedBox(
                          height: topPadding + 54,
                          child: Stack(
                            children: [
                              // Phase A: "返回首页" Header - 收缩
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

                              // Phase B: 二级页 Header - 扩张
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
                                      onBack: _handleCategoryBack,
                                    ),
                                  ),
                                ),
                            ],
                          ),
                        ),
                      ),
                    ),

                    // 列表/二级页内容区域 - 向上滑入 (Slide Up 对冲)
                    Expanded(
                      child: Transform.translate(
                        offset: Offset(0, 40 * (1 - _fadeAnimation.value)),
                        child: Stack(
                          children: [
                            // 1. 主列表
                            Opacity(
                              opacity:
                                  (1 - _expansionAnimation.value) *
                                  _fadeAnimation.value,
                              child: IgnorePointer(
                                ignoring: _activeCategoryIndex != null,
                                child: _buildMainList(),
                              ),
                            ),

                            // 2. 二级页内容
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
                                        _cachedSubPage ??
                                        const SizedBox.shrink(),
                                  ),
                                ),
                              ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget? _cachedSubPage;

  void _handleCategoryTap(int index) {
    setState(() {
      _activeCategoryIndex = index;
      _cachedSubPage = _buildThemedSubPage(context);
    });
    _expansionController.forward();
  }

  void _handleCategoryBack() {
    _expansionController.reverse().then((_) {
      setState(() {
        _activeCategoryIndex = null;
        _cachedSubPage = null;
      });
    });
  }

  /// 物理隔离：预构建二级页，隔离 AnimatedBuilder 的高频重绘。
  Widget _buildThemedSubPage(BuildContext context) {
    if (_activeCategoryIndex == null) return const SizedBox.shrink();

    final cat = categories[_activeCategoryIndex!];
    final skin =
        Theme.of(context).extension<SkinExtension>() ??
        const StarBackgroundSkin();

    return Theme(
      key: ValueKey('themed_page_${_activeCategoryIndex}'),
      data: Theme.of(
        context,
      ).copyWith(extensions: [skin.copyWith(medium: cat.color)]),
      child: cat.page,
    );
  }

  Widget _buildMainList() {
    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 20),
      child: Column(
        children: List.generate(categories.length, (index) {
          final cat = categories[index];
          return Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: KitStaggeredEntrance(
              index: index + 1,
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

  void _handleBack() {
    if (_activeCategoryIndex != null) {
      _handleCategoryBack();
      return;
    }
    _fadeController.reverse().then((_) {
      context.read<NavigationProvider>().closeSettings();
    });
  }
}
