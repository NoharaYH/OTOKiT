import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../../application/transfer/sync_settings_provider.dart';
import '../../../../application/transfer/transfer_provider.dart';
import '../../../design_system/constants/sizes.dart';
import '../../../design_system/constants/colors.dart';
import '../../../design_system/kit_shared/kit_staggered_entrance.dart';
import '../../../design_system/kit_shared/confirm_button.dart';
import '../../../design_system/kit_score_sync/score_sync_token_field.dart';

/// 设置页: 传分服务专页 (v2.0 - Refined)
/// 遵循 "Horizontal Paging Strategy" 与 "Internal Pushing Pattern" 规程。
class SyncServicePage extends StatelessWidget {
  const SyncServicePage({super.key});

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      key: const ValueKey('sync_service_page_view'),
      clipBehavior: Clip.none, // 防止阴影被裁剪
      padding: EdgeInsets.symmetric(
        horizontal: UiSizes.getHorizontalMargin(context),
        vertical: 20,
      ),
      child: ChangeNotifierProvider(
        create: (_) => SyncSettingsProvider(),
        child: Column(
          children: [
            // 卡片 A: 水鱼配置
            _SettingsCard(
              index: 1,
              title: "Diving-Fish (水鱼)",
              icon: Icons.api,
              child: const DfTokenAssembly(),
            ),

            const SizedBox(height: UiSizes.atomicComponentGap),

            // 卡片 B: 落雪 OAuth
            _SettingsCard(
              index: 3,
              title: "LXNS (落雪)",
              icon: Icons.vpn_key_outlined,
              child: const LxnsOAuthAssembly(),
            ),
          ],
        ),
      ),
    );
  }
}

/// 标准设置卡片包装器
class _SettingsCard extends StatelessWidget {
  final int index;
  final String title;
  final IconData icon;
  final Widget child;

  const _SettingsCard({
    required this.index,
    required this.title,
    required this.icon,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    return KitStaggeredEntrance(
      index: index,
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(UiSizes.cardContentPadding),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(UiSizes.cardRadius),
          boxShadow: [
            BoxShadow(
              color: UiColors.black.withValues(alpha: 0.15),
              blurRadius: 10,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              children: [
                Icon(icon, size: 20, color: UiColors.grey700),
                const SizedBox(width: 8),
                Text(
                  title,
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w900,
                    color: UiColors.grey800,
                    letterSpacing: 1.1,
                  ),
                ),
              ],
            ),
            const SizedBox(height: UiSizes.atomicComponentGap),
            child,
          ],
        ),
      ),
    );
  }
}

/// DfTokenAssembly: 包含高性能输入框组件。
class DfTokenAssembly extends StatefulWidget {
  const DfTokenAssembly({super.key});

  @override
  State<DfTokenAssembly> createState() => _DfTokenAssemblyState();
}

class _DfTokenAssemblyState extends State<DfTokenAssembly> {
  late TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    final provider = context.read<TransferProvider>();
    _controller = TextEditingController(text: provider.dfToken);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final transfer = context.watch<TransferProvider>();

    return Column(
      children: [
        ScoreSyncTokenField(
          controller: _controller,
          hint: "通过 Diving-Fish 官方查分器获取",
          onChanged: () {
            context.read<SyncSettingsProvider>().updateTempDfToken(
              _controller.text,
            );
          },
        ),
        ConfirmButton(
          text: "验证并保存 Token",
          state: transfer.isLoading
              ? ConfirmButtonState.loading
              : ConfirmButtonState.ready,
          onPressed: () {
            final tempToken = _controller.text.trim();
            if (tempToken.isEmpty) return;

            // 下沉逻辑
            transfer.updateTokens(df: tempToken);
            transfer.verifyAndSave(mode: 0, gameType: 0); // 默认为 maimai 验证，通用的
          },
        ),
      ],
    );
  }
}

/// LxnsOAuthAssembly: 落雪单点授权与状态管理
class LxnsOAuthAssembly extends StatelessWidget {
  const LxnsOAuthAssembly({super.key});

  @override
  Widget build(BuildContext context) {
    final transfer = context.watch<TransferProvider>();
    final isVerified = transfer.isLxnsVerified;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Container(
          padding: const EdgeInsets.symmetric(
            vertical: UiSizes.spaceXS,
            horizontal: UiSizes.spaceS,
          ),
          margin: const EdgeInsets.only(bottom: UiSizes.atomicComponentGap),
          decoration: BoxDecoration(
            color: isVerified
                ? Colors.green.withValues(alpha: 0.1)
                : Colors.red.withValues(alpha: 0.1),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Row(
            children: [
              Icon(
                isVerified ? Icons.check_circle : Icons.error_outline,
                size: 16,
                color: isVerified ? Colors.green : Colors.red,
              ),
              const SizedBox(width: 8),
              Text(
                isVerified ? "授权状态：有效" : "授权状态：未授权或已过期",
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                  color: isVerified ? Colors.green : Colors.red,
                ),
              ),
            ],
          ),
        ),
        ConfirmButton(
          text: "验证授权状态",
          icon: Icons.refresh,
          state: transfer.isLoading
              ? ConfirmButtonState.loading
              : ConfirmButtonState.ready,
          onPressed: () {
            // 通过重新加载 Token 或尝试验证来刷新状态
            transfer.verifyAndSave(mode: 2, gameType: 0);
          },
        ),
        const SizedBox(height: UiSizes.atomicComponentGap),
        ConfirmButton(
          text: isVerified ? "重新授权登录" : "单点授权登录",
          icon: Icons.login,
          state: transfer.isLoading
              ? ConfirmButtonState.loading
              : ConfirmButtonState.ready,
          onPressed: () => transfer.startLxnsOAuthFlow(),
        ),
      ],
    );
  }
}
