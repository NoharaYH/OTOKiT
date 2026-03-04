/// 极简注解信标，独立于 AppTheme 基类以切断生成器循环依赖。
/// 所有背景皮肤类必须挂载此注解，方可被 build_runner 自动收录。
class GameTheme {
  const GameTheme();
}
