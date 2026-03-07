import '../entities/sync_result.dart';

/// 曲库仓储端口：订阅普通曲/宴谱流、同步 OSS、计数。
/// 列表元素类型在 Phase 3 迁移 MaiMusic 到 domain 后定为 MaiMusic，当前由实现侧保证类型。
abstract class MusicLibraryRepository {
  Stream<List<Object>> watchNormalMusic();
  Stream<List<Object>> watchUtageMusic();
  Future<SyncResult> syncFromOss();
  Future<int> countNormal();
  Future<int> countUtage();
}
