import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../design_system/constants/colors.dart';
import '../../design_system/constants/animations.dart';
import '../../design_system/kit_shared/kit_bounce_scaler.dart';
import '../../design_system/kit_shared/kit_staggered_entrance.dart';
import '../../../application/shared/navigation_provider.dart';
import 'components/settings_header.dart';
import 'categories/personalization_page.dart';
import 'categories/sync_service_page.dart';
import '../../design_system/visual_skins/implementations/defaut_skin/star_background.dart';

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
      page: const SyncServicePage(),
    ),
    (
      icon: Icons.settings,
      title: "应用设置",
      color: Colors.blue,
      page: const SizedBox(),
    ),
    (
      icon: Icons.palette,
      title: "个性化设置",
      color: Colors.purpleAccent,
      page: const PersonalizationPage(),
    ),
    (
      icon: Icons.info_outline,
      color: Colors.grey,
      title: "应用信息",
      page: const SizedBox(),
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
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, result) {
        if (didPop) return;
        _handleBack();
      },
      child: AnimatedBuilder(
        animation: Listenable.merge([_fadeAnimation, _expansionAnimation]),
        builder: (context, child) {
          final topPadding = MediaQuery.of(context).padding.top;

          return Theme(
            data: Theme.of(
              context,
            ).copyWith(extensions: [const StarBackgroundSkin()]),
            child: Material(
              color: Colors.transparent,
              child: Stack(
                children: [
                  // 1. 全局背景拦截 (仅淡入)
                  Positioned.fill(
                    child: GestureDetector(
                      onTap: () => _activeCategoryIndex == null
                          ? _handleBack()
                          : _handleCategoryBack(),
                      child: Opacity(
                        opacity: _fadeAnimation.value,
                        child: BackdropFilter(
                          filter: ImageFilter.blur(sigmaX: 15.0, sigmaY: 15.0),
                          child: Container(
                            color: UiColors.white.withValues(alpha: 0.25),
                          ),
                        ),
                      ),
                    ),
                  ),

                  // 2. 核心内容区 (解耦动效)
                  Column(
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
                                      child: _buildMainHeader(topPadding),
                                    ),
                                  ),
                                ),

                                // Phase B: 二级页 Header - 扩张
                                if (_activeCategoryIndex != null)
                                  ClipRect(
                                    child: Align(
                                      alignment: Alignment.topCenter,
                                      heightFactor: _expansionAnimation.value,
                                      child: SettingsHeader(
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
                                Opacity(
                                  opacity: _expansionAnimation.value,
                                  child: Transform.translate(
                                    offset: Offset(
                                      0,
                                      40 * (1 - _expansionAnimation.value),
                                    ),
                                    child:
                                        categories[_activeCategoryIndex!].page,
                                  ),
                                ),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildMainHeader(double topPadding) {
    return Container(
      width: double.infinity,
      decoration: BoxDecoration(
        color: Colors.white,
        boxShadow: [
          BoxShadow(
            color: UiColors.black.withValues(alpha: 0.15),
            blurRadius: 10,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          SizedBox(height: topPadding),
          GestureDetector(
            onTap: () => _handleBack(),
            behavior: HitTestBehavior.opaque,
            child: Container(
              height: 54,
              padding: const EdgeInsets.symmetric(horizontal: 16),
              color: Colors.transparent,
              child: Row(
                children: [
                  KitBounceScaler(
                    onTap: () => _handleBack(),
                    child: const Icon(
                      Icons.arrow_back_ios_new,
                      size: 20,
                      color: Colors.black87,
                    ),
                  ),
                  const SizedBox(width: 12),
                  const Text(
                    "返回首页",
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                      color: Colors.black87,
                      letterSpacing: 1.2,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
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
              child: _buildCategoryItem(
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

  Widget _buildCategoryItem({
    required IconData icon,
    required String title,
    required Color iconColor,
    required VoidCallback onTap,
  }) {
    return KitBounceScaler(
      onTap: onTap,
      child: Container(
        height: 54,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(20),
          boxShadow: [
            BoxShadow(
              color: UiColors.black.withValues(alpha: 0.15),
              blurRadius: 10,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(6),
              decoration: BoxDecoration(
                color: iconColor,
                shape: BoxShape.circle,
              ),
              child: Icon(icon, color: Colors.white, size: 20),
            ),
            const SizedBox(width: 16),
            Hero(
              tag: 'category_title_$title',
              child: Material(
                color: Colors.transparent,
                child: Text(
                  title,
                  style: const TextStyle(
                    fontSize: 17,
                    fontWeight: FontWeight.w600,
                    color: Colors.black87,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _handleCategoryTap(int index) {
    setState(() {
      _activeCategoryIndex = index;
    });
    _expansionController.forward();
  }

  void _handleCategoryBack() {
    _expansionController.reverse().then((_) {
      setState(() {
        _activeCategoryIndex = null;
      });
    });
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
