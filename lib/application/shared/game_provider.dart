import 'dart:convert';
import 'package:flutter/material.dart';
import '../../kernel/models/startup_pref_model.dart';
import '../../kernel/services/storage_service.dart';
import '../../kernel/di/injection.dart';

class GameProvider extends ChangeNotifier {
  int _currentIndex = 0;
  final ValueNotifier<double> pageValueNotifier = ValueNotifier<double>(0.0);

  int get currentIndex => _currentIndex;

  /// 当前活跃的游戏标识（内存态，退出时才落盘）
  /// 由 ScoreSyncPage 通过 updateActiveContext 主动同步。
  String _activeGame = 'Mai';
  String _activeService = 'DivingFish';

  StartupPrefModel _startupPref = StartupPrefModel.defaultFallback;
  StartupPrefModel get startupPref => _startupPref;

  // --- 皮肤系统相关 ---
  bool _isIndependentSkin = false;
  bool get isIndependentSkin => _isIndependentSkin;

  String _globalSkin = 'default';
  String get globalSkin => _globalSkin;
  Color _globalThemeColor = Colors.blue;
  Color get globalThemeColor => _globalThemeColor;

  String _maimaiSkin = 'maimai_dx';
  String get maimaiSkin => _maimaiSkin;
  Color _maimaiThemeColor = Colors.green;
  Color get maimaiThemeColor => _maimaiThemeColor;

  String _chunithmSkin = 'chunithm';
  String get chunithmSkin => _chunithmSkin;
  Color _chunithmThemeColor = Colors.orange;
  Color get chunithmThemeColor => _chunithmThemeColor;

  /// 初始化：读取三段式启动页偏好并应用
  Future<void> init() async {
    final storage = getIt<StorageService>();
    final prefStr = await storage.read(StorageService.kStartupPrefConfig);
    final lastStateStr = await storage.read(StorageService.kLastActiveState);

    _startupPref = StartupPrefModel.parse(prefStr);

    int initialIndex = 0;

    if (_startupPref.needsStateObserver) {
      // 从回溯缓存中提取最后活跃状态
      final parsed = _parseLastActiveState(lastStateStr, _startupPref);
      initialIndex = parsed.index;
      _activeGame = parsed.game;
      _activeService = parsed.service;
    } else {
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
  /// 懒触发原则：仅当偏好路径包含 last/inherit 时才实际写入，否则静默跳过。
  Future<void> saveLastActiveState() async {
    if (!_startupPref.needsStateObserver) return;

    final payload = jsonEncode({
      'primary_id': _startupPref.primary.serialize(),
      'secondary_id': _activeGame,
      'tertiary_id': _activeService,
      'index': _currentIndex,
    });
    await getIt<StorageService>().save(
      StorageService.kLastActiveState,
      payload,
    );
  }

  /// 更新启动页偏好并持久化
  Future<void> setStartupPref(StartupPrefModel pref) async {
    if (_startupPref == pref) return;
    _startupPref = pref;
    await getIt<StorageService>().save(
      StorageService.kStartupPrefConfig,
      pref.serialize(),
    );
    notifyListeners();
  }

  // --- 皮肤系统逻辑更新 ---
  void setIndependentSkin(bool value) {
    if (_isIndependentSkin == value) return;
    _isIndependentSkin = value;
    notifyListeners();
  }

  void setGlobalSkin(String skin) {
    _globalSkin = skin;
    notifyListeners();
  }

  void setGlobalThemeColor(Color color) {
    _globalThemeColor = color;
    notifyListeners();
  }

  void setMaimaiSkin(String skin) {
    _maimaiSkin = skin;
    notifyListeners();
  }

  void setMaimaiThemeColor(Color color) {
    _maimaiThemeColor = color;
    notifyListeners();
  }

  void setChunithmSkin(String skin) {
    _chunithmSkin = skin;
    notifyListeners();
  }

  void setChunithmThemeColor(Color color) {
    _chunithmThemeColor = color;
    notifyListeners();
  }

  /// 解析回溯缓存，返回内容包含：game 标识、service 标识、页面索引。
  ///
  /// 局部降级规则（按 §6.4 层级限定权限）：
  /// - Sync+Inherit 场景：若缓存记录的 secondary_id 不在 {Mai, Chu} 内，
  ///   则降级至游戏默认选项而非全局降级，最大保留信息量。
  static ({int index, String game, String service}) _parseLastActiveState(
    String? raw,
    StartupPrefModel pref,
  ) {
    const defaultResult = (index: 0, game: 'Mai', service: 'DivingFish');
    if (raw == null || raw.isEmpty) return defaultResult;
    try {
      final map = jsonDecode(raw) as Map<String, dynamic>;
      final game = map['secondary_id'] as String? ?? 'Mai';
      final service = map['tertiary_id'] as String? ?? 'DivingFish';
      int index = (map['index'] as int? ?? 0).clamp(0, 1);

      // 局部降级：当 Primary==Sync 但缓存记录不是 Sync 内的游戏时，强制降级至 Mai
      if (pref.primary == StartupPrimary.sync &&
          game != 'Mai' &&
          game != 'Chu') {
        return defaultResult;
      }

      return (index: index, game: game, service: service);
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
