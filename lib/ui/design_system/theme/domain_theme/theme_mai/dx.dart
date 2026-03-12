import 'dart:math' as math;
import 'package:flutter/material.dart';
import '../../core/app_theme.dart';

@GameTheme()
class DxTheme extends AppTheme {
  const DxTheme();

  @override
  ThemeDomain get domain => ThemeDomain.maimai;

  @override
  String get themeTitle => 'DX';

  @override
  String get themeId => 'mai_dx';

  // DX 主要是浅蓝色调
  @override
  Color get light => const Color(0xFFE1F5FE);

  @override
  Color get basic => const Color(0xFF00B9EF);

  @override
  Color get dark => const Color(0xFF2D2D2D);

  @override
  Color get subtitleColor => basic;

  @override
  Color get dotColor => basic;

  @override
  Widget buildBackground(BuildContext context) {
    final w = MediaQuery.sizeOf(context).width;
    final h = MediaQuery.sizeOf(context).height;
    final bool isCompact = w < 600;

    // ── L2 地球（两档，Phase A-5）──
    final double earthW = isCompact ? double.infinity : 1101.0;
    final double earthH = isCompact ? 420.0 : 1140.0;
    final double earthBottom = isCompact ? -(h * 0.34) : -640.0;

    // ── L4 云朵 / 环 尺寸在子组件内按 isCompact 两档 ──
    // _FloatingRing 位置固定像素两档（A-6）
    final double ring1Top = isCompact ? 40.0 : 100.0;
    final double ring1Left = isCompact ? -56.0 : -140.0;
    final double ring2Top = isCompact ? 120.0 : 300.0;
    final double ring2Right = isCompact ? -24.0 : -80.0;
    final double ring3Top = isCompact ? 180.0 : 450.0;
    final double ring3Left = isCompact ? -20.0 : -50.0;

    // 彩虹/网格层仍用比例居中（Phase B 再改 L1），暂保留
    final double rainbowWidth = (isCompact ? w : 1101.0) * 0.7;
    final double rainbowTop = isCompact ? (h * 0.50 - 120) : (h * 0.50 - 200);

    return Stack(
      fit: StackFit.expand,
      children: [
        const ColoredBox(color: Color(0xFF00B9EF)),
        _RotatingEarth(
          earthW: earthW,
          earthH: earthH,
          earthBottom: earthBottom,
        ),
        Positioned(
          top: rainbowTop,
          left: (w - rainbowWidth) / 2,
          width: rainbowWidth,
          child: Stack(
            alignment: Alignment.center,
            children: [
              Image.asset(
                'assets/background/maimaidx/dx/dot_bg.webp',
                repeat: ImageRepeat.repeat,
                width: rainbowWidth,
                height: 400,
              ),
              Image.asset(
                'assets/background/maimaidx/dx/rainbow.webp',
                width: rainbowWidth,
                fit: BoxFit.contain,
              ),
            ],
          ),
        ),
        _FloatingRing(
          top: ring1Top,
          left: ring1Left,
          right: null,
          clockwise: true,
        ),
        _FloatingRing(
          top: ring2Top,
          left: null,
          right: ring2Right,
          clockwise: false,
        ),
        _FloatingRing(
          top: ring3Top,
          left: ring3Left,
          right: null,
          clockwise: true,
        ),
        const _DriftingCloud(
          topRatio: 0.15,
          speed: 0.04,
          delay: 0,
          fromRight: false,
        ),
        const _DriftingCloud(
          topRatio: 0.25,
          speed: 0.03,
          delay: 5,
          fromRight: true,
        ),
        const _DriftingCloud(
          topRatio: 0.35,
          speed: 0.06,
          delay: 10,
          fromRight: true,
        ),
      ],
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
    final safeDark = (dark != null && dark.computeLuminance() > 0.3)
        ? const Color(0xFF2D2D2D)
        : (dark ?? this.dark);
    return AppTheme.createDynamic(
      domainVal: domain,
      titleVal: themeTitle,
      idVal: themeId,
      lightColor: light ?? this.light,
      basicColor: basic ?? this.basic,
      darkColor: safeDark,
      subtitleColorVal: subtitleColor ?? this.subtitleColor,
      dotColorVal: dotColor ?? this.dotColor,
      baseTheme: this,
    );
  }
}

class _RotatingEarth extends StatefulWidget {
  final double earthW;
  final double earthH;
  final double earthBottom;

  const _RotatingEarth({
    required this.earthW,
    required this.earthH,
    required this.earthBottom,
  });

  @override
  State<_RotatingEarth> createState() => _RotatingEarthState();
}

class _RotatingEarthState extends State<_RotatingEarth>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 80),
    )..repeat();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final w = MediaQuery.sizeOf(context).width;

    return Positioned(
      bottom: widget.earthBottom,
      left: widget.earthW == double.infinity ? 0 : (w - widget.earthW) / 2,
      child: SizedBox(
        width: widget.earthW == double.infinity ? w : widget.earthW,
        height: widget.earthH,
        child: AnimatedBuilder(
          animation: _controller,
          builder: (context, child) => Transform.rotate(
            angle: 2 * math.pi * _controller.value,
            child: child,
          ),
          child: Image.asset(
            'assets/background/maimaidx/dx/earth.webp',
            width: widget.earthW,
            height: widget.earthH,
            fit: BoxFit.contain,
          ),
        ),
      ),
    );
  }
}

class _FloatingRing extends StatefulWidget {
  final double top;
  final double? left;
  final double? right;
  final bool clockwise;

  const _FloatingRing({
    required this.top,
    this.left,
    this.right,
    required this.clockwise,
  });

  @override
  State<_FloatingRing> createState() => _FloatingRingState();
}

class _FloatingRingState extends State<_FloatingRing>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 60),
    )..repeat();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final w = MediaQuery.sizeOf(context).width;
    final ringSize = w < 600 ? 80.0 : 160.0;

    return Positioned(
      top: widget.top,
      left: widget.left,
      right: widget.right,
      child: AnimatedBuilder(
        animation: _controller,
        builder: (context, child) {
          final angle = widget.clockwise
              ? 2 * math.pi * _controller.value
              : -2 * math.pi * _controller.value;
          return Transform.rotate(angle: angle, child: child);
        },
        child: Image.asset(
          'assets/background/maimaidx/dx/ring3.webp',
          width: ringSize,
          height: ringSize,
          fit: BoxFit.contain,
        ),
      ),
    );
  }
}

class _DriftingCloud extends StatefulWidget {
  final double topRatio;
  final double speed;
  final double delay;
  final bool fromRight;

  const _DriftingCloud({
    required this.topRatio,
    required this.speed,
    required this.delay,
    this.fromRight = false,
  });

  @override
  State<_DriftingCloud> createState() => _DriftingCloudState();
}

class _DriftingCloudState extends State<_DriftingCloud>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final String _assetPath;

  @override
  void initState() {
    super.initState();
    final random = math.Random();
    _assetPath =
        'assets/background/maimaidx/dx/cloud${random.nextInt(4) + 1}.webp';

    final durationSeconds = 1.3 / widget.speed;
    _controller = AnimationController(
      vsync: this,
      duration: Duration(milliseconds: (durationSeconds * 1000).toInt()),
    );

    _controller.addStatusListener((status) {
      if (status == AnimationStatus.completed) {
        final restDuration = 5 + math.Random().nextInt(6);
        Future.delayed(Duration(seconds: restDuration), () {
          if (mounted) _controller.forward(from: 0);
        });
      }
    });

    Future.delayed(Duration(seconds: widget.delay.toInt()), () {
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
    final w = MediaQuery.sizeOf(context).width;
    final h = MediaQuery.sizeOf(context).height;
    final double cloudSize = w < 600 ? 110.0 : 160.0;

    return AnimatedBuilder(
      animation: _controller,
      builder: (context, child) {
        final totalDistance = w + cloudSize;
        final double x = widget.fromRight
            ? w - (totalDistance * _controller.value)
            : -cloudSize + (totalDistance * _controller.value);
        return Positioned(
          top: h * widget.topRatio,
          left: x,
          child: child!,
        );
      },
      child: Image.asset(
        _assetPath,
        width: cloudSize,
        height: cloudSize,
        fit: BoxFit.contain,
      ),
    );
  }
}
