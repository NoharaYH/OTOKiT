import 'core/app_theme.dart';

// AUTO-IMPORTS BEGIN
import 'package:flutter_application_1/ui/design_system/theme/domain_theme/theme_chu/verse.dart';
import 'package:flutter_application_1/ui/design_system/theme/domain_theme/theme_mai/circle.dart';
import 'package:flutter_application_1/ui/design_system/theme/universal_theme/star_trails.dart';
// AUTO-IMPORTS END

part 'theme_catalog.g.dart';

/// 主题目录入口。
/// 列表与字典由 tool/theme_registry_gen.dart 自动生成，存于 theme_catalog.g.dart。
/// 外部调用方 ONLY 通过此类提供的静态接口访问主题池，
/// 不得直接依赖生成文件中的私有变量。
class ThemeCatalog {
  /// 全局主题列表
  static List<AppTheme> get universalThemes => _universalThemes;

  /// 舞萌主题列表
  static List<AppTheme> get maimaiThemes => _maimaiThemes;

  /// 中二节奏主题列表
  static List<AppTheme> get chunithmThemes => _chunithmThemes;

  /// 全部可用主题的扁平列表
  static List<AppTheme> get allThemes => [
    ..._universalThemes,
    ..._maimaiThemes,
    ..._chunithmThemes,
  ];

  /// 根据主题 ID 查找，未找到返回全局默认（universalThemes 第一项）
  static AppTheme findThemeById(String id) {
    final registry = _buildAllThemesRegistry();
    return registry[id] ?? _defaultTheme;
  }
}
