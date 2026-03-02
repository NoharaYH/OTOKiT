import 'dart:ui';
import 'package:flutter/material.dart';
import '../../../design_system/constants/colors.dart';
import '../../../design_system/kit_shared/kit_bounce_scaler.dart';

/// 动效协议 A/B/D: 支持扩张与重心位移的设置页导航头
class SettingsHeader extends StatelessWidget {
  final String title;
  final IconData icon;
  final Color iconColor;
  final VoidCallback onBack;
  final double expansionProgress; // 0.0 to 1.0

  const SettingsHeader({
    super.key,
    required this.title,
    required this.icon,
    required this.iconColor,
    required this.onBack,
    this.expansionProgress = 1.0,
  });

  @override
  Widget build(BuildContext context) {
    final topPadding = MediaQuery.of(context).padding.top;

    // 物理规程：圆角随扩张消失 (20.0 -> 0.0)
    final borderRadius = lerpDouble(20.0, 0.0, expansionProgress);

    return Container(
      width: double.infinity,
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.only(
          bottomLeft: Radius.circular(borderRadius!),
          bottomRight: Radius.circular(borderRadius),
        ),
        boxShadow: [
          BoxShadow(
            color: UiColors.black.withValues(alpha: 0.1 * expansionProgress),
            blurRadius: 10,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Padding(
        padding: EdgeInsets.only(
          top: topPadding,
          left: 16,
          right: 16,
          bottom: 0, // 移除冗余占位，确保高度计算与全局一致
        ),
        child: SizedBox(
          height: 54,
          child: Stack(
            alignment: Alignment.centerLeft,
            children: [
              // 阶段 3: 固定圆圈，身份转换
              KitBounceScaler(
                onTap: onBack,
                child: Container(
                  padding: const EdgeInsets.all(6),
                  decoration: BoxDecoration(
                    color: iconColor,
                    shape: BoxShape.circle,
                  ),
                  child: Stack(
                    alignment: Alignment.center,
                    children: [
                      // 返回图标 (二级页激活)
                      Opacity(
                        opacity: expansionProgress,
                        child: const Icon(
                          Icons.arrow_back_ios_new,
                          color: Colors.white,
                          size: 16,
                        ),
                      ),
                      // 原始图标 (卡片态)
                      Opacity(
                        opacity: 1 - expansionProgress,
                        child: Icon(icon, color: Colors.white, size: 18),
                      ),
                    ],
                  ),
                ),
              ),

              // 阶段 2: 标题扩张与位移
              Positioned(
                left: 48,
                child: Hero(
                  tag: 'category_title_$title',
                  child: Material(
                    color: Colors.transparent,
                    child: Text(
                      title,
                      style: TextStyle(
                        fontSize: lerpDouble(17.0, 20.0, expansionProgress),
                        fontWeight: FontWeight.w900,
                        color: UiColors.grey800,
                        letterSpacing: 1.1,
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  double? lerpDouble(double a, double b, double t) {
    return a + (b - a) * t;
  }
}
