/// 启动页偏好两段式路径数据模型
///
/// 格式：Primary:Secondary
/// 示例：
///   Sync:Mai     -> 启动直接进入舞萌成绩同步
///   Sync:Chu     -> 启动直接进入中二节奏成绩同步
///   Detail:Mai   -> 启动进入舞萌歌曲详情
///   Detail:Chu   -> 启动进入中二节奏歌曲详情
///   Last:None    -> 启动完全以退出时页面为准（全回溯）
///
/// 此文件不引入任何 Flutter/UI 依赖，ONLY 为纯 Dart 数据结构。

// ── 枚举定义 ──────────────────────────────────────────────────────────────────

enum StartupPrimary {
  sync,
  detail,
  last;

  String serialize() => name[0].toUpperCase() + name.substring(1);

  static StartupPrimary parse(String? raw) {
    switch (raw?.toLowerCase()) {
      case 'sync':
        return StartupPrimary.sync;
      case 'detail':
        return StartupPrimary.detail;
      default:
        return StartupPrimary.last;
    }
  }
}

enum StartupSecondary {
  mai,
  chu,
  none;

  String serialize() => name[0].toUpperCase() + name.substring(1);

  static StartupSecondary parse(String? raw) {
    switch (raw?.toLowerCase()) {
      case 'mai':
        return StartupSecondary.mai;
      case 'chu':
        return StartupSecondary.chu;
      default:
        return StartupSecondary.none;
    }
  }
}

// ── 复合模型 ──────────────────────────────────────────────────────────────────

class StartupPrefModel {
  final StartupPrimary primary;
  final StartupSecondary secondary;

  const StartupPrefModel({required this.primary, required this.secondary});

  /// 降级默认值：全回溯模式。
  static const defaultFallback = StartupPrefModel(
    primary: StartupPrimary.last,
    secondary: StartupSecondary.none,
  );

  /// 若 Primary == last，则需要激活状态回溯监听器。
  bool get needsStateObserver => primary == StartupPrimary.last;

  /// 序列化为复合路径字符串写入存储。
  String serialize() => '${primary.serialize()}:${secondary.serialize()}';

  /// 从存储字符串解析，兼容旧三段式格式。
  /// 格式异常时统一降级为 defaultFallback。
  static StartupPrefModel parse(String? raw) {
    if (raw == null || raw.isEmpty) return defaultFallback;
    final segments = raw.split(':');
    // 兼容旧三段格式（Primary:Secondary:Tertiary），忽略第三段
    if (segments.length < 2) return defaultFallback;
    return StartupPrefModel(
      primary: StartupPrimary.parse(segments[0]),
      secondary: StartupSecondary.parse(segments[1]),
    );
  }

  @override
  bool operator ==(Object other) =>
      other is StartupPrefModel &&
      other.primary == primary &&
      other.secondary == secondary;

  @override
  int get hashCode => Object.hash(primary, secondary);

  @override
  String toString() => 'StartupPrefModel(${serialize()})';
}
