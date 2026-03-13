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
    final screenWidth = MediaQuery.of(context).size.width;
    final screenHeight = MediaQuery.of(context).size.height;
    // 继承 _RotatingEarth 的地球比例 1.7
    final earthWidth = screenWidth * 1.7;
    // 窄于地球宽度的 10%
    final rainbowWidth = earthWidth * 0.7;

    return Stack(
      fit: StackFit.expand,
      children: [
        // 0. 基础填充层
        const ColoredBox(color: Color(0xFF00B9EF)),

        // 4. 地球 (旋转且放大)
        const _RotatingEarth(),

        // 1 & 2. 复合网格与彩虹层 (宽度为地球的 90%，中心点设在屏幕 50% 高度)
        Positioned(
          top: screenHeight * 0.50 - 200,
          left: (screenWidth - rainbowWidth) / 2,
          width: rainbowWidth,
          child: Stack(
            alignment: Alignment.center,
            children: [
              // 蓝色网格 (平铺纹理)
              Image.asset(
                'assets/background/maimaidx/dx/dot_bg.webp',
                repeat: ImageRepeat.repeat,
                width: rainbowWidth,
                height: 400,
              ),
              // 彩虹
              Image.asset(
                'assets/background/maimaidx/dx/rainbow.webp',
                width: rainbowWidth,
                fit: BoxFit.contain,
              ),
            ],
          ),
        ),

        // 3. 按照标注位置分布的小环形装饰 (层级最上方)
        const _FloatingRing(
          top: 0.1,
          left: -0.14,
          size: 0.30,
          clockwise: true,
        ), // 左上角
        const _FloatingRing(
          top: 0.3,
          right: -0.08,
          size: 0.3,
          clockwise: false,
        ), // 右侧中段边缘
        const _FloatingRing(
          top: 0.45,
          left: -0.05,
          size: 0.20,
          clockwise: true,
        ), // 左下彩虹区域
        // 5. 漂浮云朵 (1个从左向右，2个从右向左，始终保持屏幕最多3个)
        const _DriftingCloud(
          top: 0.15,
          speed: 0.04,
          delay: 0,
          fromRight: false,
        ),
        const _DriftingCloud(top: 0.25, speed: 0.03, delay: 5, fromRight: true),
        const _DriftingCloud(
          top: 0.35,
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
  const _RotatingEarth();

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
    final screenWidth = MediaQuery.of(context).size.width;
    // 放大 120%
    final earthWidth = screenWidth * 1.8;

    return Positioned(
      // 固定圆心在屏幕正下方边缘：
      // 底部偏移为半径的负值，左侧偏移使中心对齐
      bottom: -earthWidth / 2,
      left: (screenWidth - earthWidth) / 2,
      child: AnimatedBuilder(
        animation: _controller,
        builder: (context, child) {
          // 匀速顺时针旋转且整体水平拉宽 15%
          return Transform.scale(
            scaleX: 1.15,
            child: Transform.rotate(
              angle: 2 * math.pi * _controller.value,
              child: child,
            ),
          );
        },
        child: Image.asset(
          'assets/background/maimaidx/dx/earth.webp',
          width: earthWidth,
          height: earthWidth,
          fit: BoxFit.contain,
        ),
      ),
    );
  }
}

class _FloatingRing extends StatefulWidget {
  final double top;
  final double? left;
  final double? right;
  final double size; // 屏幕宽度的比例
  final bool clockwise;

  const _FloatingRing({
    required this.top,
    this.left,
    this.right,
    required this.size,
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
    // 速度与地球相同：60s 一圈
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
    final screenWidth = MediaQuery.of(context).size.width;
    final screenHeight = MediaQuery.of(context).size.height;
    final ringSize = screenWidth * widget.size;

    return Positioned(
      top: screenHeight * widget.top,
      left: widget.left != null ? screenWidth * widget.left! : null,
      right: widget.right != null ? screenWidth * widget.right! : null,
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
  final double top;
  final double speed;
  final double delay;
  final bool fromRight;

  const _DriftingCloud({
    required this.top,
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
  late final double _cloudWidth;

  @override
  void initState() {
    super.initState();
    // 随机选择一个云朵资源
    final random = math.Random();
    _assetPath =
        'assets/background/maimaidx/dx/cloud${random.nextInt(4) + 1}.webp';
    _cloudWidth = 100.0 + random.nextDouble() * 100.0;

    // 计算动画时长：(1 + 宽度占比) / 速度
    // 假设云朵宽度约为 0.3 屏幕宽，总行程为 1.3
    final durationSeconds = 1.3 / widget.speed;

    _controller = AnimationController(
      vsync: this,
      duration: Duration(milliseconds: (durationSeconds * 1000).toInt()),
    );

    _controller.addStatusListener((status) {
      if (status == AnimationStatus.completed) {
        // 完成一次漂流后，随机休息 5-10 秒再开始下一次
        final restDuration = 5 + math.Random().nextInt(6);
        Future.delayed(Duration(seconds: restDuration), () {
          if (mounted) _controller.forward(from: 0);
        });
      }
    });

    // 延迟启动
    Future.delayed(Duration(seconds: widget.delay.toInt()), () {
      if (mounted) {
        _controller.forward();
      }
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final screenWidth = MediaQuery.of(context).size.width;
    final screenHeight = MediaQuery.of(context).size.height;

    return AnimatedBuilder(
      animation: _controller,
      builder: (context, child) {
        final totalDistance = screenWidth + _cloudWidth;
        double x;

        if (widget.fromRight) {
          // 从右侧外部屏幕移动到左侧外部
          x = screenWidth - (totalDistance * _controller.value);
        } else {
          // 从左侧外部屏幕移动到右侧外部
          x = -_cloudWidth + (totalDistance * _controller.value);
        }

        return Positioned(
          top: screenHeight * widget.top,
          left: x,
          child: child!,
        );
      },
      child: Image.asset(_assetPath, width: _cloudWidth, fit: BoxFit.contain),
    );
  }
}
