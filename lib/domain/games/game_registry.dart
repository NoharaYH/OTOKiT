import '../value_objects/game_type.dart';
import 'game_module.dart';

/// 游戏模块注册表，由 Bootstrap/DI 注入所有 GameModule 实现。
/// 上层通过 findByType / findById 获取，扩展新游戏时仅追加注册，无需改上层代码。
class GameRegistry {
  const GameRegistry(this._modules);
  final List<GameModule> _modules;

  GameModule? findByType(GameType type) {
    for (final m in _modules) {
      if (m.gameId == type.gameId) return m;
    }
    return null;
  }

  GameModule? findById(String gameId) {
    for (final m in _modules) {
      if (m.gameId == gameId) return m;
    }
    return null;
  }

  List<GameModule> get all => List<GameModule>.unmodifiable(_modules);
}
