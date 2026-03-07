/// 难度集合值对象，供传分配置使用。
class DifficultySet {
  const DifficultySet(this.values);
  final Set<int> values;

  static const all = DifficultySet({0, 1, 2, 3, 4, 5});

  bool isValid() => values.every((d) => d >= 0 && d <= 10);
}
