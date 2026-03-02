import 'package:flutter/material.dart';
import '../../../design_system/constants/assets.dart';
import 'score_sync_logo_wrapper.dart';
import 'score_sync_assembly.dart';

class ChuSyncPage extends StatelessWidget {
  final int mode;
  final ValueChanged<int> onModeChanged;

  const ChuSyncPage({
    super.key,
    required this.mode,
    required this.onModeChanged,
  });

  @override
  Widget build(BuildContext context) {
    return ScoreSyncLogoWrapper(
      logoPath: AppAssets.logoChunithm,
      subtitle: 'CHUNITHM Prober',
      child: ScoreSyncAssembly(
        key: const ValueKey('ScoreSyncAssembly_Chu'),
        mode: mode,
        onModeChanged: onModeChanged,
        gameType: 1,
      ),
    );
  }
}
