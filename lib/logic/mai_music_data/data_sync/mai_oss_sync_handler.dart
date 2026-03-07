import 'package:injectable/injectable.dart';

import '../data_formats/mai_song_row.dart';

enum SyncPhase { idle, pulling, writing }

/// OSS 提供两个 JSON：普通曲、宴谱。拉取后分别解析为 [MaiMusicRow] 与 [MaiUtageRow]。
@injectable
class MaiOssSyncHandler {
  /// 实际 URL 存于 test/sql/secrets/oss_urls.json（该目录已 gitignore），此处不硬编码。
  static const String ossNormalUrl = '';
  static const String ossUtageUrl = '';

  bool _isSyncing = false;
  bool get isSyncing => _isSyncing;

  /// 从 OSS 拉取两个 JSON，返回 (普通曲列表, 宴谱列表)。
  /// 任一 URL 未配置或拉取失败时该侧为 null；完全失败返回 null。
  Future<({List<MaiMusicRow>? normal, List<MaiUtageRow>? utage})?> performSync({
    void Function(SyncPhase)? onPhaseChanged,
  }) async {
    if (_isSyncing) return null;
    _isSyncing = true;
    onPhaseChanged?.call(SyncPhase.pulling);
    try {
      // TODO: 并行或串行 GET ossNormalUrl / ossUtageUrl，解析为 List<MaiMusicRow> / List<MaiUtageRow>
      // onPhaseChanged?.call(SyncPhase.writing);
      // return (normal: normalList, utage: utageList);
      return null;
    } finally {
      _isSyncing = false;
      onPhaseChanged?.call(SyncPhase.idle);
    }
  }
}
