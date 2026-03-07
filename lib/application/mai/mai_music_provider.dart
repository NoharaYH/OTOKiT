import 'dart:async';

import 'package:flutter/material.dart';
import 'package:injectable/injectable.dart';

import '../../kernel/storage/sql/daos/mai_music_dao.dart';
import '../../logic/mai_music_data/data_formats/mai_music.dart';
import '../../logic/mai_music_data/data_sync/mai_oss_sync_handler.dart';
import '../../logic/mai_music_data/transform/mai_db_mapper.dart';
import '../shared/toast_provider.dart';

@injectable
class MaiMusicProvider extends ChangeNotifier {
  MaiMusicProvider(this._maiMusicDao, this._syncHandler, this._toastProvider);

  final MaiMusicDao _maiMusicDao;
  final MaiOssSyncHandler _syncHandler;
  final ToastProvider _toastProvider;

  StreamSubscription? _musicSubscription;
  StreamSubscription? _utageSubscription;

  List<MaiMusic> _musics = [];
  List<MaiMusic> _utageMusics = [];

  bool _isInitialized = false;
  bool _isLoading = false;
  bool _isUtageMode = false;
  SyncPhase _syncPhase = SyncPhase.idle;

  bool get isInitialized => _isInitialized;
  bool get isLoading => _isLoading;
  bool get isUtageMode => _isUtageMode;
  SyncPhase get syncPhase => _syncPhase;
  int get syncCurrent => 0;
  int get syncTotal => 0;
  bool get hasData => _musics.isNotEmpty;
  List<MaiMusic> get musics => _isUtageMode ? _utageMusics : _musics;
  List<MaiMusic> get utageMusics => _utageMusics;

  void toggleUtageMode() {
    _isUtageMode = !_isUtageMode;
    notifyListeners();
  }

  @override
  void dispose() {
    _musicSubscription?.cancel();
    _utageSubscription?.cancel();
    super.dispose();
  }

  /// 初始化：订阅普通曲库流与宴谱流
  Future<void> init() async {
    if (_isInitialized) return;

    _isLoading = true;
    notifyListeners();

    _musicSubscription = _maiMusicDao.watchSongs().listen((rows) {
      _musics = rows.map((r) => MaiDbMapper.fromTable(r)).toList();
      _checkInitialized();
    });

    _utageSubscription = _maiMusicDao.watchUtageSongs().listen((rows) {
      _utageMusics = rows.map((r) => MaiDbMapper.fromUtageTable(r)).toList();
      _checkInitialized();
    });
  }

  void _checkInitialized() {
    _isInitialized = true;
    _isLoading = false;
    notifyListeners();
  }

  /// 从 OSS 拉取两个 JSON 并分别写入两表
  Future<void> sync() async {
    if (_isLoading) return;
    _isLoading = true;
    notifyListeners();

    try {
      final result = await _syncHandler.performSync(
        onPhaseChanged: (phase) {
          _syncPhase = phase;
          notifyListeners();
        },
      );

      if (result != null) {
        int normalCount = 0;
        int utageCount = 0;
        if (result.normal != null && result.normal!.isNotEmpty) {
          await _maiMusicDao.batchInsertNormal(result.normal!);
          normalCount = result.normal!.length;
        }
        if (result.utage != null && result.utage!.isNotEmpty) {
          await _maiMusicDao.batchInsertUtage(result.utage!);
          utageCount = result.utage!.length;
        }
        if (normalCount > 0 || utageCount > 0) {
          final parts = <String>[];
          if (normalCount > 0) parts.add('普通 $normalCount 首');
          if (utageCount > 0) parts.add('宴谱 $utageCount 首');
          _toastProvider.show(
            "曲库同步成功 (${parts.join('，')})",
            ToastType.confirmed,
          );
        } else {
          _toastProvider.show("曲库已是最新", ToastType.confirmed);
        }
      } else {
        _toastProvider.show("曲库已是最新", ToastType.confirmed);
      }
    } catch (e) {
      debugPrint('MaiMusicProvider: Sync failed: $e');
      _toastProvider.show("同步失败: $e", ToastType.error);
    } finally {
      _isLoading = false;
      _syncPhase = SyncPhase.idle;
      notifyListeners();
    }
  }

  /// 搜索普通曲
  List<MaiMusic> search(String query) {
    if (query.isEmpty) return _musics;
    final search = query.toLowerCase();
    return _musics
        .where(
          (m) =>
              m.basicInfo.title.toLowerCase().contains(search) ||
              m.basicInfo.id.toString() == search,
        )
        .toList();
  }

  /// 搜索宴谱
  List<MaiMusic> searchUtage(String query) {
    if (query.isEmpty) return _utageMusics;
    final search = query.toLowerCase();
    return _utageMusics
        .where(
          (m) =>
              m.basicInfo.title.toLowerCase().contains(search) ||
              m.basicInfo.id.toString() == search,
        )
        .toList();
  }
}
