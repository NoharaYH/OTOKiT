import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:injectable/injectable.dart';

import '../../kernel/models/startup_pref_model.dart';
import '../../kernel/models/theme_preferences_model.dart';
import '../../kernel/services/storage_service.dart';
import '../../ui/design_system/theme/core/app_theme.dart';
import 'navigation_provider.dart';

@injectable
class GameProvider extends ChangeNotifier {
  GameProvider(this._storageService);

  final StorageService _storageService;
  int _currentIndex = 0;
  final ValueNotifier<double> pageValueNotifier = ValueNotifier<double>(0.0);

  int get currentIndex => _currentIndex;

  /// 当前活跃的游戏标识（内存态，退出时才落盘）
  /// 由 ScoreSyncPage 通过 updateActiveContext 主动同步。
  String _activeGame = 'Mai';
  String _activeService = 'DivingFish';

  StartupPrefModel _startupPref = StartupPrefModel.defaultFallback;
  StartupPrefModel get startupPref => _startupPref;

  // --- 主题偏好 (Factory Pump 注水层) ---
  ThemePreferencesModel _themePrefs = ThemePreferencesModel.empty;
  ThemePreferencesModel get themePrefs => _themePrefs;

  /// 当前全局活跃皮肤 ID（默认为 star_trails）
  String _activeSkinId = 'star_trails';
  String get activeSkinId => _activeSkinId;

  // --- 主题应用模式与独立皮肤 ---
  bool _isThemeGlobal = true;
  bool get isThemeGlobal => _isThemeGlobal;

  String _maiSkinId = 'mai_circle';
  String get maiSkinId => _maiSkinId;

  String _chuSkinId = 'chu_verse';
  String get chuSkinId => _chuSkinId;

  /// 初始化：读取启动页偏好并应用，返回目标大页面 tag 供调用方注入 NavigationProvider。
  Future<PageTag> init() async {
    final prefStr = await _storageService.read(StorageService.kStartupPrefConfig);
    final lastStateStr = await _storageService.read(StorageService.kLastActiveState);
    final themePrefsStr = await _storageService.read(StorageService.kThemePreferences);

    _startupPref = StartupPrefModel.parse(prefStr);
    _themePrefs = ThemePreferencesModel.parse(themePrefsStr);
    _activeSkinId =
        await _storageService.read(StorageService.kActiveSkinId) ?? 'star_trails';

    final themeModeStr = await _storageService.read(StorageService.kThemeMode);
    _isThemeGlobal = themeModeStr != 'independent';
    _maiSkinId = await _storageService.read(StorageService.kMaiSkinId) ?? 'mai_circle';
    _chuSkinId = await _storageService.read(StorageService.kChuSkinId) ?? 'chu_verse';

    int initialIndex = 0;
    PageTag initialTag = PageTag.scoreSync;

    if (_startupPref.needsStateObserver) {
      // 从回溯缓存中提取最后活跃状态（含大页面 Tag）
      final parsed = _parseLastActiveState(lastStateStr, _startupPref);
      initialIndex = parsed.index;
      _activeGame = parsed.game;
      _activeService = parsed.service;
      initialTag = parsed.tag;
    } else {
      // Primary 决定目标大页面
      switch (_startupPref.primary) {
        case StartupPrimary.detail:
          initialTag = PageTag.musicData;
          break;
        default:
          initialTag = PageTag.scoreSync;
          break;
      }
      // Secondary 决定具体游戏索引
      switch (_startupPref.secondary) {
        case StartupSecondary.chu:
          initialIndex = 1;
          _activeGame = 'Chu';
          break;
        case StartupSecondary.mai:
        default:
          initialIndex = 0;
          _activeGame = 'Mai';
          break;
      }
    }

    _currentIndex = initialIndex.clamp(0, 1);
    pageValueNotifier.value = _currentIndex.toDouble();
    notifyListeners();
    return initialTag;
  }

  void setIndex(int index) {
    if (_currentIndex != index) {
      _currentIndex = index.clamp(0, 1);
      notifyListeners();
    }
  }

  void onPageChanged(int index) {
    setIndex(index);
  }

  /// 页内模式切换时调用（不写存储）。
  /// 在 App 退出时由 saveLastActiveState 统一落盘。
  void updateActiveContext({required String game, required String service}) {
    _activeGame = game;
    _activeService = service;
  }

  /// 在 App 退出/挂起时调用，持久化当前活跃状态。
  /// [currentPageTag] 由 RootPage 的生命周期监听器传入，记录用户真实所处的大页面。
  /// 懒触发原则：仅当偏好路径为 Last 模式时才实际写入，否则静默跳过。
  Future<void> saveLastActiveState(PageTag currentPageTag) async {
    if (!_startupPref.needsStateObserver) return;

    // primary_id 写入真实的大页面标识（而非静态偏好枚举），确保下次回溯正确
    final primaryIdStr = currentPageTag == PageTag.musicData
        ? 'Detail'
        : 'Sync';

    final payload = jsonEncode({
      'primary_id': primaryIdStr,
      'secondary_id': _activeGame,
      'tertiary_id': _activeService,
      'index': _currentIndex,
    });
    await _storageService.save(
      StorageService.kLastActiveState,
      payload,
    );
  }

  /// 更新启动页偏好并持久化
  Future<void> setStartupPref(StartupPrefModel pref) async {
    if (_startupPref == pref) return;
    _startupPref = pref;
    await _storageService.save(
      StorageService.kStartupPrefConfig,
      pref.serialize(),
    );
    notifyListeners();
  }

  // --- 主题偏好逻辑 (Factory Pump) ---

  /// 将用户自定义色通过 copyWith 注水到基础主题实例。
  /// 若当前主题无任何自定义记录，直接返回 [base] 本身，零分配。
  AppTheme resolvedTheme(AppTheme base) {
    if (!_themePrefs.hasCustomization(base.themeId)) return base;
    return base.copyWith(
      light: _themePrefs.get(base.themeId, 'light'),
      basic: _themePrefs.get(base.themeId, 'basic'),
      dark: _themePrefs.get(base.themeId, 'dark'),
      dotColor: _themePrefs.get(base.themeId, 'dotColor'),
      subtitleColor: _themePrefs.get(base.themeId, 'subtitleColor'),
    );
  }

  /// 将新的主题偏好应用到内存并异步落盘。
  /// 由调色面板的 Debouncer 决议具体调用时机。
  Future<void> setThemePreferences(ThemePreferencesModel prefs) async {
    if (_themePrefs == prefs) return;
    _themePrefs = prefs;
    await _storageService.save(
      StorageService.kThemePreferences,
      prefs.serialize(),
    );
    notifyListeners();
  }

  /// 切换当前全局皮肤并持久化。
  /// [skinId] 必须是 skin_catalog.allSkins 中存在的 skinId。
  Future<void> setActiveSkin(String skinId) async {
    if (_activeSkinId == skinId) return;
    _activeSkinId = skinId;
    await _storageService.save(StorageService.kActiveSkinId, skinId);
    notifyListeners();
  }

  Future<void> setThemeMode(bool isGlobal) async {
    if (_isThemeGlobal == isGlobal) return;
    _isThemeGlobal = isGlobal;
    await _storageService.save(
      StorageService.kThemeMode,
      isGlobal ? 'global' : 'independent',
    );
    notifyListeners();
  }

  Future<void> setMaiSkin(String skinId) async {
    if (_maiSkinId == skinId) return;
    _maiSkinId = skinId;
    await _storageService.save(StorageService.kMaiSkinId, skinId);
    notifyListeners();
  }

  Future<void> setChuSkin(String skinId) async {
    if (_chuSkinId == skinId) return;
    _chuSkinId = skinId;
    await _storageService.save(StorageService.kChuSkinId, skinId);
    notifyListeners();
  }

  // --- 弃用字段兼容桌 (COMPAT_STUBS) ---
  // 原有的 setGlobalSkin / setMaimaiSkin 等内存字段已午退 (无持久化)。
  // 如果其他页面依然引用它们，可删除对应调用再展开其他即可。

  /// 解析回溯缓存，返回内容包含：game 标识、service 标识、页面索引、大页面 Tag。
  ///
  /// primary_id 字段现在存储真实的大页面标识（"Sync" | "Detail"），而非静态偏好枚举。
  /// 局部降级规则：ScoreSync 场景下若 secondary_id 不在 {Mai, Chu} 内则全局降级。
  static ({int index, String game, String service, PageTag tag})
  _parseLastActiveState(String? raw, StartupPrefModel pref) {
    const defaultResult = (
      index: 0,
      game: 'Mai',
      service: 'DivingFish',
      tag: PageTag.scoreSync,
    );
    if (raw == null || raw.isEmpty) return defaultResult;
    try {
      final map = jsonDecode(raw) as Map<String, dynamic>;
      final primaryStr = map['primary_id'] as String? ?? 'Sync';
      final game = map['secondary_id'] as String? ?? 'Mai';
      final service = map['tertiary_id'] as String? ?? 'DivingFish';
      final int index = (map['index'] as int? ?? 0).clamp(0, 1);

      // 由 primary_id 字符串还原大页面 tag
      final tag = (primaryStr.toLowerCase() == 'detail')
          ? PageTag.musicData
          : PageTag.scoreSync;

      // 局部降级：ScoreSync 场景只允许 Mai/Chu 的 game 标识
      if (tag == PageTag.scoreSync && game != 'Mai' && game != 'Chu') {
        return defaultResult;
      }

      return (index: index, game: game, service: service, tag: tag);
    } catch (_) {
      return defaultResult;
    }
  }

  @override
  void dispose() {
    pageValueNotifier.dispose();
    super.dispose();
  }
}
