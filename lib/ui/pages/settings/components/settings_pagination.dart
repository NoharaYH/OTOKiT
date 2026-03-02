import 'package:flutter/material.dart';
import '../../../design_system/kit_shared/kit_game_carousel.dart';
import '../../../design_system/kit_shared/game_page_item.dart';

/// 动效协议 C: 封装横向翻页策略 (Paging Strategy)
/// 使用 KitGameCarousel 引擎。以解决长列表配置的视觉疲劳。
class SettingsPagination extends StatefulWidget {
  final List<GamePageItem> categories;
  final int initialPage;
  final ValueChanged<int>? onPageChanged;
  final PageController? controller;

  const SettingsPagination({
    super.key,
    required this.categories,
    this.initialPage = 0,
    this.onPageChanged,
    this.controller,
  });

  @override
  State<SettingsPagination> createState() => _SettingsPaginationState();
}

class _SettingsPaginationState extends State<SettingsPagination> {
  late PageController _internalController;
  PageController get _pageController =>
      widget.controller ?? _internalController;

  @override
  void initState() {
    super.initState();
    if (widget.controller == null) {
      _internalController = PageController(initialPage: widget.initialPage);
    }
  }

  @override
  void dispose() {
    if (widget.controller == null) {
      _internalController.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: KitGameCarousel(
        items: widget.categories,
        controller: _pageController,
        onPageChanged: widget.onPageChanged,
      ),
    );
  }
}
