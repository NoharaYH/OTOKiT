import 'package:flutter/material.dart';
import 'package:injectable/injectable.dart';

import '../../logic/chu_music_data/data_formats/chu_music.dart';
import '../../logic/chu_music_data/data_sync/chu_sync_handler.dart';
import '../../logic/chu_music_data/library/chu_library.dart';

@injectable
class ChuMusicDataProvider extends ChangeNotifier {
  final ChuLibrary _library = ChuLibrary();
  final ChuSyncHandler _syncHandler = ChuSyncHandler();

  bool _isLoading = false;
  bool get isLoading => _isLoading;
  List<ChuMusic> get musics => _library.musics;

  Future<void> init() async {
    await _library.initialize();
    notifyListeners();
  }

  Future<void> sync() async {
    _isLoading = true;
    notifyListeners();
    final newSongs = await _syncHandler.performSync();
    if (newSongs != null) {
      await _library.updateAndPersist(newSongs);
    }
    _isLoading = false;
    notifyListeners();
  }
}
