/// 曲库同步结果，供 MusicLibraryRepository.syncFromOss 返回。
class SyncResult {
  const SyncResult({this.normalCount = 0, this.utageCount = 0});

  final int normalCount;
  final int utageCount;
}
