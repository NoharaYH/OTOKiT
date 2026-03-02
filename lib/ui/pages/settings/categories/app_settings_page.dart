import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../../kernel/models/startup_pref_model.dart';
import '../../../../application/shared/game_provider.dart';
import '../../../design_system/constants/sizes.dart';
import '../../../design_system/visual_skins/skin_extension.dart';
import '../../../design_system/kit_setting/setting_card.dart';
import '../../../design_system/kit_setting/setting_menu.dart';
import '../../../design_system/kit_setting/setting_expandable_menu.dart';

/// 设置页: 应用设置专页 (v4.0 - Two-Tier Startup Pref)
/// 专注于应用流程控制，实现两级联动启动页偏好设置。
class AppSettingsPage extends StatelessWidget {
  const AppSettingsPage({super.key, Color? themeColor});

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      key: const ValueKey('app_settings_page_view'),
      clipBehavior: Clip.none,
      padding: EdgeInsets.symmetric(
        horizontal: UiSizes.getHorizontalMargin(context),
        vertical: 20,
      ),
      child: const Column(
        children: [
          SettingCard(
            index: 1,
            title: '启动页偏好',
            icon: Icons.launch_outlined,
            child: StartupPrefMenu(),
          ),
        ],
      ),
    );
  }
}

/// 两级联动启动页偏好选择器。
///
/// 临时状态在 State 内闭环（短期记忆隔离机制），每次选择后立即 commit 写入 Provider。
class StartupPrefMenu extends StatefulWidget {
  const StartupPrefMenu({super.key});

  @override
  State<StartupPrefMenu> createState() => _StartupPrefMenuState();
}

class _StartupPrefMenuState extends State<StartupPrefMenu> {
  late StartupPrimary _tempPrimary;
  late StartupSecondary _tempSecondary;

  @override
  void initState() {
    super.initState();
    final pref = context.read<GameProvider>().startupPref;
    _tempPrimary = pref.primary;
    _tempSecondary = pref.secondary;
  }

  void _onPrimarySelect(StartupPrimary value) {
    setState(() {
      _tempPrimary = value;
      _tempSecondary = StartupSecondary.none;
    });
    _commit();
  }

  void _onSecondarySelect(StartupSecondary value) {
    setState(() => _tempSecondary = value);
    _commit();
  }

  void _commit() {
    context.read<GameProvider>().setStartupPref(
      StartupPrefModel(primary: _tempPrimary, secondary: _tempSecondary),
    );
  }

  @override
  Widget build(BuildContext context) {
    final skin = Theme.of(context).extension<SkinExtension>()!;
    final accent = skin.medium;

    final showSecondary =
        _tempPrimary == StartupPrimary.sync ||
        _tempPrimary == StartupPrimary.detail;

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        // ── 一级下拉选择器（始终显示）────────────────────────
        SettingMenu<StartupPrimary>(
          options: const [
            StartupPrimary.sync,
            StartupPrimary.detail,
            StartupPrimary.last,
          ],
          labels: const ['成绩同步页', '歌曲详情页', '以退出时页面为准'],
          current: _tempPrimary,
          onSelect: _onPrimarySelect,
          accentColor: accent,
          leadingIcon: Icons.launch_outlined,
        ),

        // ── 二级可展开下拉选择器（仅 sync/detail 时显示）──────
        SettingExpandableMenu<StartupSecondary>(
          isExpanded: showSecondary,
          expansionKey: 'secondary_${_tempPrimary.name}',
          sectionLabel: '选择游戏',
          indent: 16,
          options: const [StartupSecondary.mai, StartupSecondary.chu],
          labels: const ['舞萌 DX', '中二节奏'],
          current: _tempSecondary == StartupSecondary.none
              ? StartupSecondary.mai
              : _tempSecondary,
          onSelect: _onSecondarySelect,
          accentColor: accent,
          leadingIcon: Icons.sports_esports_outlined,
        ),
      ],
    );
  }
}
