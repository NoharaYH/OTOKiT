import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:provider/provider.dart';

import '../../../application/shared/game_provider.dart';
import '../../../application/shared/navigation_provider.dart';
import 'music_data/music_data_page.dart';
import 'score_sync/score_sync_page.dart';
import 'settings/settings_page.dart';
import '../design_system/kit_navigation/nav_deck_overlay.dart';
import '../design_system/constants/sizes.dart';
import '../design_system/visual_skins/implementations/defaut_skin/star_background.dart';
import '../design_system/kit_shared/kit_action_circle.dart';
import '../design_system/page_shell.dart';

class RootPage extends StatefulWidget {
  const RootPage({super.key});

  @override
  State<RootPage> createState() => _RootPageState();
}

class _RootPageState extends State<RootPage> {
  final GlobalKey _repaintBoundaryKey = GlobalKey();

  @override
  void initState() {
    super.initState();
    // 注册全局设置页捕获协议
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        context.read<NavigationProvider>().captureTask = _captureToSnapshot;
      }
    });
  }

  Future<void> _captureToSnapshot() async {
    try {
      final boundary =
          _repaintBoundaryKey.currentContext?.findRenderObject()
              as RenderRepaintBoundary?;
      if (boundary == null) return;

      // 降额捕获 (Downsampling): 既然要模糊，1.0 的像素比足够快且省内存
      final ui.Image image = await boundary.toImage(pixelRatio: 1.0);
      if (mounted) {
        context.read<NavigationProvider>().setBgSnapshot(image);
      }
    } catch (e) {
      debugPrint('Snapshot Capture Failed: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        // ── 全量快照捕获区 (RepaintBoundary) ────────────────
        // 必须包裹 PageShell 及其背景层，才能捕获完整的视觉画面。
        RepaintBoundary(
          key: _repaintBoundaryKey,
          child: PageShell(
            backgroundOverride: AnimatedBuilder(
              animation: context.read<GameProvider>().pageValueNotifier,
              builder: (context, _) {
                return const StarBackgroundSkin().buildBackground(context);
              },
            ),
            child: Stack(
              children: [
                // 1. 业务内容层
                Consumer<NavigationProvider>(
                  builder: (context, nav, child) {
                    Widget page;
                    switch (nav.currentTag) {
                      case PageTag.scoreSync:
                        page = const ScoreSyncPage(key: ValueKey('scoreSync'));
                        break;
                      case PageTag.musicData:
                        page = const MusicDataPage(key: ValueKey('musicData'));
                        break;
                    }

                    return AnimatedSwitcher(
                      duration: const Duration(milliseconds: 400),
                      switchInCurve: const Interval(
                        0.5,
                        1.0,
                        curve: Curves.easeOutCubic,
                      ),
                      switchOutCurve: const Interval(
                        0.5,
                        1.0,
                        curve: Curves.easeInCubic,
                      ),
                      transitionBuilder: (child, animation) {
                        return FadeTransition(opacity: animation, child: child);
                      },
                      child: page,
                    );
                  },
                ),

                // 2. 侧边栏隐形呼出热区
                Positioned(
                  left: 0,
                  top: 0,
                  bottom: 0,
                  width: MediaQuery.of(context).size.width * 0.04,
                  child: Consumer<NavigationProvider>(
                    builder: (context, nav, child) {
                      return GestureDetector(
                        behavior: HitTestBehavior.translucent,
                        onPanUpdate: (details) {
                          if (details.delta.dx > 5 && !nav.isDeckOpen) {
                            nav.openDeck(anchorY: details.globalPosition.dy);
                          }
                        },
                      );
                    },
                  ),
                ),

                // 3. 全局页眉操作区
                AnimatedBuilder(
                  animation: context.read<GameProvider>().pageValueNotifier,
                  builder: (context, _) {
                    const skin = StarBackgroundSkin();
                    final themeColor = skin.medium;

                    return Positioned(
                      top: UiSizes.getTopMarginWithSafeArea(context) + 12.0,
                      right: UiSizes.getHorizontalMargin(context) + 12.0,
                      child: Consumer<NavigationProvider>(
                        builder: (context, nav, child) {
                          return Container(
                            padding: EdgeInsets.zero,
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                KitActionCircle(
                                  icon: Icons.settings,
                                  color: themeColor,
                                  onTap: () => nav.openSettings(),
                                ),
                                const SizedBox(width: UiSizes.spaceS),
                                Builder(
                                  builder: (btnCtx) => KitActionCircle(
                                    icon: Icons.menu_open,
                                    color: themeColor,
                                    onTap: () {
                                      final RenderBox box =
                                          btnCtx.findRenderObject()
                                              as RenderBox;
                                      final position = box.localToGlobal(
                                        Offset.zero,
                                      );
                                      nav.openDeck(
                                        anchorY: position.dy + box.size.height,
                                      );
                                    },
                                  ),
                                ),
                              ],
                            ),
                          );
                        },
                      ),
                    );
                  },
                ),

                // 4. 悬浮胶囊覆盖层
                AnimatedBuilder(
                  animation: context.read<GameProvider>().pageValueNotifier,
                  builder: (context, _) {
                    const skin = StarBackgroundSkin();
                    return Theme(
                      data: Theme.of(context).copyWith(extensions: [skin]),
                      child: const Positioned.fill(child: NavDeckOverlay()),
                    );
                  },
                ),
              ],
            ),
          ),
        ),

        // 5. 设置页叠加层 (在外层平级挂载，不参与快照捕获)
        Consumer<NavigationProvider>(
          builder: (context, nav, child) {
            return AnimatedSwitcher(
              duration: const Duration(milliseconds: 300),
              switchInCurve: Curves.easeOutCubic,
              switchOutCurve: Curves.easeInCubic,
              child: nav.isSettingsOpen
                  ? const SettingsPage(key: ValueKey('settings_overlay'))
                  : const SizedBox.shrink(key: ValueKey('empty_overlay')),
            );
          },
        ),
      ],
    );
  }
}
