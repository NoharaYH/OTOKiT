import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../application/mai/mai_music_provider.dart';

import '../../../application/shared/game_provider.dart';

import '../../design_system/constants/assets.dart';

import '../../design_system/kit_shared/kit_game_carousel.dart';
import '../../design_system/kit_shared/game_page_item.dart';

import '../score_sync/components/score_sync_logo_wrapper.dart';
import '../../design_system/visual_skins/implementations/defaut_skin/star_background.dart';

import 'components/mai_music_assembly.dart';
import 'components/chu_music_assembly.dart';

class MusicDataPage extends StatefulWidget {
  const MusicDataPage({super.key});

  @override
  State<MusicDataPage> createState() => _MusicDataPageState();
}

class _MusicDataPageState extends State<MusicDataPage> {
  late final PageController _localController;

  @override
  void initState() {
    super.initState();
    final gameProvider = context.read<GameProvider>();
    // 初始化本地控制器，初始页码同步全局索引
    _localController = PageController(initialPage: gameProvider.currentIndex);

    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        gameProvider.pageValueNotifier.value = _localController.initialPage
            .toDouble();
      }
    });

    _localController.addListener(() {
      if (_localController.hasClients && _localController.page != null) {
        gameProvider.pageValueNotifier.value = _localController.page!;
      }
    });

    WidgetsBinding.instance.addPostFrameCallback((_) async {
      final provider = context.read<MaiMusicProvider>();
      await provider.init();
    });
  }

  @override
  void dispose() {
    _localController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // 监听全局游戏类型变化，但使用本地控制器渲染
    final gameProvider = context.read<GameProvider>();

    return KitGameCarousel(
      controller: _localController,
      onPageChanged: (index) {
        // 同步回全局索引，确保切回同步页时状态一致
        gameProvider.setIndex(index);
      },
      items: [
        GamePageItem(
          skin: const StarBackgroundSkin(),
          title: 'Maimai DX',
          content: ScoreSyncLogoWrapper(
            logoPath: AppAssets.logoMaimai,
            subtitle: 'MUSIC LIBRARY',
            themeColor: const StarBackgroundSkin().medium,
            child: const MaiMusicAssembly(),
          ),
        ),
        GamePageItem(
          skin: const StarBackgroundSkin(),
          title: 'Chunithm',
          content: ScoreSyncLogoWrapper(
            logoPath: AppAssets.logoChunithm,
            subtitle: 'MUSIC LIBRARY',
            themeColor: const StarBackgroundSkin().medium,
            child: const ChuMusicAssembly(),
          ),
        ),
      ],
    );
  }
}
