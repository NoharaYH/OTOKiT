import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:injectable/injectable.dart';

enum PageTag { scoreSync, musicData }

@injectable
class NavigationProvider extends ChangeNotifier {
  PageTag _currentTag = PageTag.scoreSync;
  bool _isDeckOpen = false;
  bool _isSettingsOpen = false;
  double _anchorY = 0.0;

  /// 背景快照位图 (Snapshot Isolation)
  ui.Image? _bgSnapshot;
  ui.Image? get bgSnapshot => _bgSnapshot;

  void setBgSnapshot(ui.Image? img) {
    if (_bgSnapshot != img) {
      _bgSnapshot?.dispose();
    }
    _bgSnapshot = img;
    notifyListeners();
  }

  /// 缓存垃圾回收池 (用于 Layer Swap 即时变轨)
  final List<ui.Image> _snapshotGarbage = [];

  void registerTempSnapshot(ui.Image img) {
    _snapshotGarbage.add(img);
  }

  void clearBgSnapshot() {
    for (var img in _snapshotGarbage) {
      img.dispose();
    }
    _snapshotGarbage.clear();
    _bgSnapshot?.dispose();
    _bgSnapshot = null;
    notifyListeners();
  }

  // 用于交错动画的弹簧物理参数，按需存储
  // double _deckScrollOffset = 0.0;

  PageTag get currentTag => _currentTag;
  bool get isDeckOpen => _isDeckOpen;
  bool get isSettingsOpen => _isSettingsOpen;
  double get anchorY => _anchorY;

  /// 启动时由外部（ScoreSyncPage init 回调）注入初始 Tag，不发通知。
  /// 此方法仅在 Widget 首帧渲染前（postFrameCallback 内）调用。
  void setInitialTag(PageTag tag) {
    _currentTag = tag;
  }

  /// 切换页面 (无全局路由，原地挂载)
  void switchTo(PageTag tag) {
    if (_currentTag == tag) {
      if (_isDeckOpen) closeDeck();
      return;
    }
    _currentTag = tag;
    _isDeckOpen = false; // 切换后默认收起导航
    notifyListeners();
  }

  /// 展开悬浮胶囊导航栈
  void openDeck({required double anchorY}) {
    if (_isDeckOpen) return;
    _isDeckOpen = true;
    _anchorY = anchorY;
    notifyListeners();
  }

  /// 收起悬浮胶囊导航栈
  void closeDeck() {
    if (!_isDeckOpen) return;
    _isDeckOpen = false;
    notifyListeners();
  }

  /// 拖拽更新锚点位置
  void updateAnchor(double y) {
    if (!_isDeckOpen) return;
    _anchorY = y;
    notifyListeners();
  }

  /// 外部设置的捕获任务 (用于在打开前执行 RepaintBoundary.toImage)
  Future<ui.Image?> Function()? captureTask;

  /// 打开设置页（作为叠加层）
  void openSettings() async {
    if (_isSettingsOpen) return;
    if (_isDeckOpen) _isDeckOpen = false;

    // 如果注册了捕获任务，则优先执行 (Snapshot Isolation)
    if (captureTask != null) {
      final img = await captureTask!();
      setBgSnapshot(img);
    }

    _isSettingsOpen = true;
    notifyListeners();
  }

  /// 关闭设置页
  void closeSettings() {
    _isSettingsOpen = false;
    notifyListeners();
  }
}
