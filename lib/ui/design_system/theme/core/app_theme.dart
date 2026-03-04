import 'package:flutter/material.dart';
export 'theme_annotation.dart';

/// 强约束域划分
enum ThemeDomain { universal, maimai, chunithm }

// GameTheme 注解已迁移至 theme_annotation.dart，此处通过 export 透出。

/// 全新主题基类：AppTheme
abstract class AppTheme extends ThemeExtension<AppTheme> {
  const AppTheme();

  /// 对应的域（全局/舞萌/中二）
  ThemeDomain get domain;

  /// 用于下拉选单展示的名称
  String get themeTitle;

  /// 必须与 SKIN_COLOR.json 里的键名一致
  String get themeId;

  /// 亮色调
  Color get light;

  /// 中性色调
  Color get medium;

  /// 暗色调，抽象/子类实现必须保证最后兜底为 #2d2d2d
  Color get dark;

  /// 副标题颜色
  Color get subtitleColor;

  /// 页面指示点颜色
  Color get dotColor;

  /// 渲染背景 Widget
  Widget buildBackground(BuildContext context);

  @override
  AppTheme copyWith({
    Color? light,
    Color? medium,
    Color? dark,
    Color? subtitleColor,
    Color? dotColor,
  });

  /// 提供一个统一的动态实例工厂供 copyWith 返回
  static AppTheme createDynamic({
    required ThemeDomain domainVal,
    required String titleVal,
    required String idVal,
    required Color lightColor,
    required Color mediumColor,
    required Color darkColor,
    required Color subtitleColorVal,
    required Color dotColorVal,
    required AppTheme baseTheme,
  }) {
    return _DynamicAppTheme(
      domainVal: domainVal,
      titleVal: titleVal,
      idVal: idVal,
      lightColor: lightColor,
      mediumColor: mediumColor,
      darkColor: darkColor,
      subtitleColorVal: subtitleColorVal,
      dotColorVal: dotColorVal,
      baseTheme: baseTheme,
    );
  }

  @override
  AppTheme lerp(ThemeExtension<AppTheme>? other, double t) {
    if (other is! AppTheme) return this;
    return _DynamicAppTheme(
      domainVal: domain,
      titleVal: themeTitle,
      idVal: themeId,
      lightColor: Color.lerp(light, other.light, t) ?? light,
      mediumColor: Color.lerp(medium, other.medium, t) ?? medium,
      darkColor: Color.lerp(dark, other.dark, t) ?? dark,
      subtitleColorVal:
          Color.lerp(subtitleColor, other.subtitleColor, t) ?? subtitleColor,
      dotColorVal: Color.lerp(dotColor, other.dotColor, t) ?? dotColor,
      baseTheme: t < 0.5 ? this : other,
    );
  }
}

/// 纯粹的动态变体类，承载用户自定义色彩并代理原主背景逻辑。
class _DynamicAppTheme extends AppTheme {
  final ThemeDomain domainVal;
  final String titleVal;
  final String idVal;

  final Color lightColor;
  final Color mediumColor;
  final Color darkColor;
  final Color subtitleColorVal;
  final Color dotColorVal;

  final AppTheme baseTheme;

  const _DynamicAppTheme({
    required this.domainVal,
    required this.titleVal,
    required this.idVal,
    required this.lightColor,
    required this.mediumColor,
    required this.darkColor,
    required this.subtitleColorVal,
    required this.dotColorVal,
    required this.baseTheme,
  });

  @override
  ThemeDomain get domain => domainVal;

  @override
  String get themeTitle => titleVal;

  @override
  String get themeId => idVal;

  @override
  Color get light => lightColor;

  @override
  Color get medium => mediumColor;

  @override
  Color get dark => darkColor;

  @override
  Color get subtitleColor => subtitleColorVal;

  @override
  Color get dotColor => dotColorVal;

  @override
  Widget buildBackground(BuildContext context) =>
      baseTheme.buildBackground(context);

  @override
  AppTheme copyWith({
    Color? light,
    Color? medium,
    Color? dark,
    Color? subtitleColor,
    Color? dotColor,
  }) {
    return _DynamicAppTheme(
      domainVal: domainVal,
      titleVal: titleVal,
      idVal: idVal,
      lightColor: light ?? lightColor,
      mediumColor: medium ?? mediumColor,
      darkColor: dark ?? darkColor,
      subtitleColorVal: subtitleColor ?? subtitleColorVal,
      dotColorVal: dotColor ?? dotColorVal,
      baseTheme: baseTheme,
    );
  }
}
