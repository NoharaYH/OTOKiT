import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../../application/shared/game_provider.dart';
import '../../../design_system/constants/sizes.dart';
import '../../../design_system/constants/strings.dart';
import '../../../design_system/constants/colors.dart';
import '../../../design_system/kit_shared/kit_bounce_scaler.dart';
import '../../../design_system/visual_skins/skin_extension.dart';
import '../../../design_system/kit_shared/kit_staggered_entrance.dart';

/// 设置页: 个性化配置页 (v1.0)
/// 遵循 "Horizontal Paging Strategy" 规程。
class PersonalizationPage extends StatelessWidget {
  const PersonalizationPage({super.key});

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      key: const ValueKey('personalization_page_view'),
      padding: EdgeInsets.symmetric(
        horizontal: UiSizes.getHorizontalMargin(context),
      ),
      child: Column(
        children: [
          // 标题与启动页选项 (Index 0-3)
          _buildSection(
            context,
            index: 0,
            title: UiStrings.startupPage,
            child: const StartupPageSelector(baseIndex: 1),
          ),
          const SizedBox(height: 24),
          // 皮肤系统 (Index 4+)
          _buildSection(
            context,
            index: 4,
            title: "皮肤系统",
            child: const ThemeSelectorMatrix(index: 5),
          ),
        ],
      ),
    );
  }

  Widget _buildSection(
    BuildContext context, {
    required int index,
    required String title,
    required Widget child,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        KitStaggeredEntrance(
          index: index,
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 12),
            child: Text(
              title,
              style: const TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.bold,
                color: UiColors.grey700,
              ),
            ),
          ),
        ),
        child,
      ],
    );
  }
}

/// StartupPageSelector: 托管启动页偏好设置逻辑。 UI 表现通过 KitBounceScaler 增强。
class StartupPageSelector extends StatelessWidget {
  final int baseIndex;
  const StartupPageSelector({super.key, required this.baseIndex});

  @override
  Widget build(BuildContext context) {
    final gameProvider = context.watch<GameProvider>();
    final skin = Theme.of(context).extension<SkinExtension>()!;

    final options = [
      (title: UiStrings.startupMai, pref: StartupPagePref.mai),
      (title: UiStrings.startupChu, pref: StartupPagePref.chu),
      (title: UiStrings.startupLast, pref: StartupPagePref.last),
    ];

    return Column(
      children: List.generate(options.length, (i) {
        final opt = options[i];
        final isLast = i == options.length - 1;

        return Padding(
          padding: EdgeInsets.only(bottom: isLast ? 0 : 12),
          child: KitStaggeredEntrance(
            index: baseIndex + i,
            child: _buildStartupOption(
              context,
              key: ValueKey('startup_opt_${opt.pref.name}'),
              title: opt.title,
              isSelected: gameProvider.startupPref == opt.pref,
              onTap: () => gameProvider.setStartupPref(opt.pref),
              skin: skin,
            ),
          ),
        );
      }),
    );
  }

  Widget _buildStartupOption(
    BuildContext context, {
    required Key key,
    required String title,
    required bool isSelected,
    required VoidCallback onTap,
    required SkinExtension skin,
  }) {
    return KitBounceScaler(
      key: key,
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
        decoration: BoxDecoration(
          color: isSelected
              ? skin.medium.withValues(alpha: 0.1)
              : UiColors.white.withValues(alpha: 0.5),
          borderRadius: BorderRadius.circular(UiSizes.buttonRadius),
          border: Border.all(
            color: isSelected ? skin.medium : UiColors.transparent,
            width: 1.5,
          ),
        ),
        child: Row(
          children: [
            Icon(
              isSelected ? Icons.radio_button_checked : Icons.radio_button_off,
              color: isSelected ? skin.medium : UiColors.grey400,
              size: 20,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                title,
                style: TextStyle(
                  fontSize: 15,
                  fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                  color: isSelected ? skin.medium : UiColors.grey700,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// ThemeSelectorMatrix: 主题切换矩阵。强插 SkinExtension 插值渲染。
class ThemeSelectorMatrix extends StatelessWidget {
  final int index;
  const ThemeSelectorMatrix({super.key, required this.index});

  @override
  Widget build(BuildContext context) {
    return KitStaggeredEntrance(
      index: index,
      child: Container(
        key: const ValueKey('theme_matrix_placeholder'),
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: UiColors.white.withValues(alpha: 0.5),
          borderRadius: BorderRadius.circular(UiSizes.panelRadius),
        ),
        child: const Center(
          child: Text(
            "主题插值矩阵占位 (Slot 3)",
            style: TextStyle(color: UiColors.grey500, fontSize: 13),
          ),
        ),
      ),
    );
  }
}
