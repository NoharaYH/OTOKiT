import 'dart:math' as math;
import 'package:flutter/material.dart';
import '../../core/app_theme.dart';
import '../../../constants/assets.dart';

@GameTheme()
class VerseTheme extends AppTheme {
  const VerseTheme();

  @override
  ThemeDomain get domain => ThemeDomain.chunithm;

  @override
  String get themeTitle => 'Verse';

  @override
  String get themeId => 'chu_verse';

  @override
  Color get light => const Color(0xFFDBEFFF);

  @override
  Color get basic => const Color.fromARGB(255, 111, 140, 255);

  @override
  Color get dark => const Color(0xFF333333);

  @override
  Color get subtitleColor => basic;

  @override
  Color get dotColor => basic;

  @override
  Widget buildBackground(BuildContext context) {
    final w = MediaQuery.sizeOf(context).width;
    final h = MediaQuery.sizeOf(context).height;
    final bool isCompact = w < 600;

    // ── L2 焦点层：官网 .globe 三层（globe 240s、globe_center 60s 反向+缩放、globe_cross 静态）──
    final double globeSize = isCompact ? 200.0 : 420.0;   // 官网 PC 420 / SP 200
    final double globeBeforeSize = isCompact ? 225.0 : 450.0; // ::before 450/290
    final double globeCrossSize = isCompact ? 200.0 : 420.0;

    // ── L2 城镇装饰 ──
    final double townW = isCompact ? double.infinity : 1500.0;
    final double townH = isCompact ? 280.0 : 733.0;
    final double townLeft = isCompact ? 0.0 : -515.0;

    // ── L3 边角层（官网 SP 88、opacity 0.8）──
    final double topRightW = isCompact ? double.infinity : 845.0;
    final double? topRightH = isCompact ? 88.0 : null;
    final double bottomLeftW = isCompact ? double.infinity : 923.0;
    final double? bottomLeftH = isCompact ? 88.0 : null;

    // ── L4 氛围层：腰带左右、漂浮文案（两档）──
    final double beltWidth = isCompact ? 80.0 : 160.0;
    final double floatingTextW = isCompact ? 180.0 : 360.0;
    final double floatingTextTopRatio = 0.12;

    return TickerMode(
      enabled: true,
      child: Stack(
        fit: StackFit.expand,
        clipBehavior: Clip.hardEdge,
        children: [
          // L0 底色层（官网 #adf4f8）
          const Positioned.fill(
            child: ColoredBox(color: Color(0xFFADF4F8)),
          ),
          Positioned.fill(
            child: Image.asset(AppAssets.chunithmBg, fit: BoxFit.cover),
          ),
          // L2 地球：官网 .globe 顺序为 十字(底) → ::before globe 旋转 → ::after globe_center 脉冲
          // 十字静态（最底）
          Positioned.fill(
            child: Center(
              child: SizedBox(
                width: globeCrossSize,
                height: globeCrossSize,
                child: Image.asset(
                  AppAssets.chunithmVerseGlobeCross,
                  fit: BoxFit.contain,
                ),
              ),
            ),
          ),
          // ::before = globe.png 旋转 240s linear
          Positioned.fill(
            child: ClipRect(
              child: Center(
                child: _VerseRotatingGlobe(
                  key: const ValueKey<String>('verse_globe_outer'),
                  assetPath: AppAssets.chunithmVerseGlobe,
                  size: globeBeforeSize,
                  duration: const Duration(seconds: 240),
                ),
              ),
            ),
          ),
          // ::after = globe_center 60s ease reverse + scale 0.6→1→0.6
          Positioned.fill(
            child: ClipRect(
              child: Center(
                child: _VerseGlobeCenterPulse(
                  key: const ValueKey<String>('verse_globe_center'),
                  assetPath: AppAssets.chunithmVerseGlobeCenter,
                  size: globeSize,
                  duration: const Duration(seconds: 60),
                ),
              ),
            ),
          ),
        // L2 城镇：可能溢出，单独裁切
        Positioned(
          left: townLeft,
          bottom: 0,
          width: townW,
          height: townH,
          child: ClipRect(
            child: Image.asset(
              AppAssets.chunithmVerseTown,
              fit: BoxFit.contain,
              alignment: Alignment.bottomCenter,
            ),
          ),
        ),
        // L4 腰带左（官网 verseBeltLeft 15s linear infinite，opacity + 漂移）
        Positioned(
          left: 0,
          top: 0,
          bottom: 0,
          child: ClipRect(
            child: SizedBox(
              width: beltWidth,
              child: _VerseBeltDrift(
                assetPath: AppAssets.chunithmVerseBeltLeft,
                width: beltWidth,
                duration: const Duration(seconds: 15),
                isLeft: true,
              ),
            ),
          ),
        ),
        // L4 腰带右（官网 verseBeltRight 15s）
        Positioned(
          right: 0,
          top: 0,
          bottom: 0,
          child: ClipRect(
            child: SizedBox(
              width: beltWidth,
              child: _VerseBeltDrift(
                assetPath: AppAssets.chunithmVerseBeltRight,
                width: beltWidth,
                duration: const Duration(seconds: 15),
                isLeft: false,
              ),
            ),
          ),
        ),
        // L3 右上角（官网 opacity 0.8）
        Positioned(
          top: 0,
          right: 0,
          child: Opacity(
            opacity: 0.8,
            child: SizedBox(
              width: topRightH != null ? w : topRightW,
              height: topRightH,
              child: Image.asset(
                AppAssets.chunithmTopRight,
                fit: BoxFit.contain,
              ),
            ),
          ),
        ),
        // L3 左下角（官网 opacity 0.8）
        Positioned(
          bottom: 0,
          left: 0,
          child: Opacity(
            opacity: 0.8,
            child: SizedBox(
              width: bottomLeftH != null ? w : bottomLeftW,
              height: bottomLeftH,
              child: Image.asset(
                AppAssets.chunithmBottomLeft,
                fit: BoxFit.contain,
              ),
            ),
          ),
        ),
        // L4 漂浮文案（最上层，避免被角标或毛玻璃遮挡）
        Positioned(
          top: h * floatingTextTopRatio,
          left: 0,
          right: 0,
          child: Center(
            child: SizedBox(
              width: floatingTextW,
              child: Image.asset(
                AppAssets.chunithmVerseFloatingText,
                fit: BoxFit.contain,
              ),
            ),
          ),
        ),
        ],
      ),
    );
  }

  @override
  AppTheme copyWith({
    Color? light,
    Color? basic,
    Color? dark,
    Color? subtitleColor,
    Color? dotColor,
  }) {
    return AppTheme.createDynamic(
      domainVal: domain,
      titleVal: themeTitle,
      idVal: themeId,
      lightColor: light ?? this.light,
      basicColor: basic ?? this.basic,
      darkColor: dark ?? this.dark,
      subtitleColorVal: subtitleColor ?? this.subtitleColor,
      dotColorVal: dotColor ?? this.dotColor,
      baseTheme: this,
    );
  }
}

/// L2 外圈旋转（官网 .globe::before = globe.png 240s linear）
class _VerseRotatingGlobe extends StatefulWidget {
  final String assetPath;
  final double size;
  final Duration duration;

  const _VerseRotatingGlobe({
    super.key,
    required this.assetPath,
    required this.size,
    required this.duration,
  });

  @override
  State<_VerseRotatingGlobe> createState() => _VerseRotatingGlobeState();
}

class _VerseRotatingGlobeState extends State<_VerseRotatingGlobe>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: widget.duration)
      ..repeat();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: widget.size,
      height: widget.size,
      child: AnimatedBuilder(
        animation: _controller,
        builder: (context, child) {
          final double angle = _controller.value * 2 * math.pi;
          return Transform.rotate(angle: angle, child: child!);
        },
        child: Image.asset(widget.assetPath, fit: BoxFit.contain),
      ),
    );
  }
}

/// L2 中心球官网 .globe::after = globe_center 60s ease infinite reverse + scale 0.6→1→0.6
class _VerseGlobeCenterPulse extends StatefulWidget {
  final String assetPath;
  final double size;
  final Duration duration;

  const _VerseGlobeCenterPulse({
    super.key,
    required this.assetPath,
    required this.size,
    required this.duration,
  });

  @override
  State<_VerseGlobeCenterPulse> createState() => _VerseGlobeCenterPulseState();
}

class _VerseGlobeCenterPulseState extends State<_VerseGlobeCenterPulse>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: widget.duration)
      ..repeat(reverse: true);
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
        final double t = _controller.value;
        final double angle = -t * 2 * math.pi;
        final double scale = 0.6 + 0.4 * math.sin(math.pi * t);
        return Transform.rotate(
          angle: angle,
          child: Transform.scale(
            scale: scale,
            child: child,
          ),
        );
      },
      child: SizedBox(
        width: widget.size,
        height: widget.size,
        child: Image.asset(widget.assetPath, fit: BoxFit.contain),
      ),
    );
  }
}

/// L4 腰带漂移（官网 verseBeltLeft/verseBeltRight 15s：opacity 0→1→1→0 + translate）
class _VerseBeltDrift extends StatefulWidget {
  final String assetPath;
  final double width;
  final Duration duration;
  final bool isLeft;

  const _VerseBeltDrift({
    required this.assetPath,
    required this.width,
    required this.duration,
    required this.isLeft,
  });

  @override
  State<_VerseBeltDrift> createState() => _VerseBeltDriftState();
}

class _VerseBeltDriftState extends State<_VerseBeltDrift>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: widget.duration)
      ..repeat();
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
        final double t = _controller.value;
        final double opacity =
            (t <= 0.04 || t >= 0.96) ? 0.0 : 1.0;
        final double dx = (-0.4 + 0.8 * t) * widget.width;
        final double dy = (-0.4 + 0.8 * t) * widget.width * 0.6;
        return Opacity(
          opacity: opacity,
          child: Transform.translate(
            offset: Offset(widget.isLeft ? dx : -dx, dy),
            child: child,
          ),
        );
      },
      child: Image.asset(
        widget.assetPath,
        fit: BoxFit.contain,
        alignment:
            widget.isLeft ? Alignment.centerLeft : Alignment.centerRight,
      ),
    );
  }
}
