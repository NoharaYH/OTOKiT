import 'package:flutter/material.dart';
import 'kit_animation_engine.dart';

/// KitStaggeredEntrance: 配置项序列入场包装器。
/// 遵循 "Staggered Interval" 规程，按照 index 自动计算入场延迟。
class KitStaggeredEntrance extends StatefulWidget {
  final Widget child;
  final int index;
  final Duration? delay;

  const KitStaggeredEntrance({
    super.key,
    required this.child,
    required this.index,
    this.delay,
  });

  @override
  State<KitStaggeredEntrance> createState() => _KitStaggeredEntranceState();
}

class _KitStaggeredEntranceState extends State<KitStaggeredEntrance>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _opacity;
  late Animation<Offset> _slide;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: KitAnimationEngine.expandDuration,
    );

    // 透明度渐变：在动画的前 60% 完成
    _opacity = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(
        parent: _controller,
        curve: const Interval(0.0, 0.6, curve: Curves.easeIn),
      ),
    );

    // 位移渐变：从底部由下至上平滑滑入 (20px 偏移)
    _slide = Tween<Offset>(begin: const Offset(0, 20), end: Offset.zero)
        .animate(
          CurvedAnimation(
            parent: _controller,
            curve: KitAnimationEngine.decelerateCurve,
          ),
        );

    // 序列化延迟：每项间隔 125ms (遵循增加 150% 的 UI 深度规程)
    final startDelay =
        widget.delay ?? Duration(milliseconds: widget.index * 75);

    Future.delayed(startDelay, () {
      if (mounted) _controller.forward();
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, child) {
        return Transform.translate(
          offset: _slide.value,
          child: Opacity(opacity: _opacity.value, child: widget.child),
        );
      },
    );
  }
}
