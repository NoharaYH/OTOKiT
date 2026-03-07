/// 游戏类型值对象，替换裸 int 0/1。
/// 纯 Dart，无框架依赖。
enum GameType {
  maimai,
  chunithm;

  String get gameId => name;
}
