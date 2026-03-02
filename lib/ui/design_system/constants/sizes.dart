import 'package:flutter/material.dart';

class UiSizes {
  // --- Global Layout Multipliers ---
  static const double shellMarginTopMultiplier = 0.05;
  static const double shellMarginSideMultiplier = 0.03;

  // --- Spacing System ---
  static const double spaceXXS = 4.0;
  static const double spaceXS = 8.0;
  static const double spaceS = 12.0;
  static const double spaceM = 16.0;
  static const double spaceL = 24.0;
  static const double spaceXL = 32.0;

  // --- Semantic Gaps ---
  static const double atomicComponentGap = spaceS;
  static const double cardContentPadding = spaceS;
  static const double screenEdgeMargin = spaceS;

  // --- Border Radius ---
  static const double cardRadius = 20.0;
  static const double buttonRadius = 12.0;
  static const double inputRadius = 12.0;
  static const double panelRadius = 16.0;

  // --- Component Specific Heights ---
  static const double inputFieldHeight = 44.0;
  static const double logoHeight = 84.0;
  static const double logoWidth = 280.0;
  static const double logoAreaHeight = 110.0;
  static const double minLogPanelHeight = 100.0;
  static const double fallbackLogPanelHeight = 180.0;

  // --- Score Sync Layout (Used Height excluding Log Panel) ---
  static const double scoreSyncUsedHeightMai = 405.0;
  static const double scoreSyncUsedHeightLxns = 464.0;

  // --- Layout Calculations ---
  static double getHorizontalMargin(BuildContext context) => screenEdgeMargin;

  static double getTopMargin(BuildContext context) {
    return MediaQuery.of(context).size.height * shellMarginTopMultiplier;
  }

  static double getTopMarginWithSafeArea(BuildContext context) {
    final double base = getTopMargin(context);
    final double safeTop = MediaQuery.of(context).padding.top;
    return safeTop > base ? safeTop + spaceXS : base;
  }

  static EdgeInsets getPageContentPadding(BuildContext context) {
    return EdgeInsets.symmetric(horizontal: screenEdgeMargin);
  }

  static double getCardBottomMargin(BuildContext context) {
    return spaceS * 2;
  }

  static double getLogPanelMaxHeight(
    BuildContext context,
    double currentUsedHeight,
  ) {
    final screenHeight = MediaQuery.of(context).size.height;
    final topMargin = getTopMarginWithSafeArea(context);
    final bottomMargin = getCardBottomMargin(context);

    final available =
        screenHeight - topMargin - currentUsedHeight - bottomMargin;
    return available > minLogPanelHeight ? available : fallbackLogPanelHeight;
  }

  static double getDotIndicatorTop(BuildContext context) {
    return getTopMarginWithSafeArea(context) +
        logoAreaHeight +
        (atomicComponentGap / 2) -
        4.0 -
        15.0; // 上挪 12px
  }
}
