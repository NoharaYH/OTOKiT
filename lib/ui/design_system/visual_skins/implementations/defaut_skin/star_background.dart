import 'dart:math' as math;
import 'dart:ui';
import 'package:flutter/material.dart';
import '../../skin_extension.dart';

/// 默认星轨背景皮肤 - 同时作用于舞萌和中二 Standard Background
class StarBackgroundSkin extends SkinExtension {
  const StarBackgroundSkin();

  // ==================== 主题色定义 (基于 HTML 中的冷色调) ====================

  @override
  Color get light => const Color(0xFF1A4FBD); // 蓝色流光

  @override
  Color get medium => const Color(0xFF6A1EBD); // 紫色流光 - 主题色

  @override
  Color get dark => const Color(0xFF05080A); // 深邃底色

  @override
  Color get subtitleColor => Colors.white; // 默认星轨背景下使用白色副标题

  @override
  Color get dotColor => Colors.white; // 默认背景下指示点采用纯白色

  @override
  Widget buildBackground(BuildContext context) {
    return _StarBackground(
      lightColor: light,
      mediumColor: medium,
      darkColor: dark,
    );
  }

  @override
  SkinExtension copyWith({
    Color? light,
    Color? medium,
    Color? dark,
    Color? subtitleColor,
    Color? dotColor,
  }) {
    // 皮肤是常量，lerp 会通过 ThemeSkin 处理
    return this;
  }
}

class _StarBackground extends StatefulWidget {
  final Color lightColor;
  final Color mediumColor;
  final Color darkColor;

  const _StarBackground({
    required this.lightColor,
    required this.mediumColor,
    required this.darkColor,
  });

  @override
  State<_StarBackground> createState() => _StarBackgroundState();
}

class _StarBackgroundState extends State<_StarBackground>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final List<_AuroraBlobDNA> _auroraBlobs;
  final math.Random _random = math.Random(1337); // 固定种子保证 UI 稳定性
  // 记录初始化时间戳，用于计算相对时间，避免大基数导致的浮点精度丢失
  late final int _baseTime = DateTime.now().millisecondsSinceEpoch;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 20),
    )..repeat(reverse: true);

    // 基于 DNA 的色块预定义 (融入皮肤色系并增加辅助色)
    _auroraBlobs = _generateBlobDNA();
  }

  List<_AuroraBlobDNA> _generateBlobDNA() {
    // 注入亮色、中性色以及对比色实现复杂流光
    final coreColors = [
      widget.lightColor,
      widget.mediumColor,
      const Color(0xFF0891B2), // Cyan 600
      const Color(0xFFDB2777), // Pink 600
      const Color(0xFF4F46E5), // Indigo 600
      widget.lightColor.withValues(alpha: 0.8),
    ];

    return List.generate(6, (index) {
      return _AuroraBlobDNA(
        startX: _random.nextDouble() * 1.5 - 0.25,
        endX: _random.nextDouble() * 1.5 - 0.25,
        startY: _random.nextDouble() * 1.5 - 0.25,
        endY: _random.nextDouble() * 1.5 - 0.25,
        scaleBase: 0.8 + _random.nextDouble() * 0.7,
        rotationMultiplier:
            (_random.nextDouble() > 0.5 ? 1 : -1) *
            (0.5 + _random.nextDouble()),
        phaseOffset: _random.nextDouble() * math.pi * 2,
        color: coreColors[index % coreColors.length].withValues(
          alpha: 0.68,
        ), // 亮度提高 50% (0.45 * 1.5)
      );
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      fit: StackFit.expand,
      children: [
        Container(color: widget.darkColor),

        // 1. 流光层：Aurora Mesh Gradient
        Positioned.fill(
          child: ImageFiltered(
            imageFilter: ImageFilter.blur(sigmaX: 80, sigmaY: 80),
            child: AnimatedBuilder(
              animation: _controller,
              builder: (context, _) {
                final t = _controller.value;
                final curveT = Curves.easeInOut.transform(t);
                final size = MediaQuery.of(context).size;

                return Stack(
                  children: _auroraBlobs.map((dna) {
                    return _buildAuroraBlob(size, dna, curveT);
                  }).toList(),
                );
              },
            ),
          ),
        ),

        // 2. 星轨层
        Positioned.fill(
          child: AnimatedBuilder(
            animation: _controller,
            builder: (context, _) {
              // 使用相对时间偏移，确保传入 Painter 的数值量级在安全范围内
              final now = DateTime.now().millisecondsSinceEpoch;
              final relativeMs = now - _baseTime;
              return CustomPaint(
                painter: _StarTrailPainter(elapsedMs: relativeMs),
              );
            },
          ),
        ),

        // 3. 噪点与阴影叠加层 (提升质感)
        Positioned.fill(
          child: DecoratedBox(
            decoration: BoxDecoration(
              gradient: RadialGradient(
                center: Alignment.bottomRight,
                radius: 1.5,
                colors: [
                  Colors.transparent,
                  widget.darkColor.withValues(alpha: 0.7),
                ],
                stops: const [0.3, 1.0],
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildAuroraBlob(Size screenSize, _AuroraBlobDNA dna, double t) {
    // 大小提高 100% (0.9 -> 1.8)
    final baseSize = math.min(screenSize.width, screenSize.height) * 1.8;

    // 位置计算
    final x = lerpDouble(
      dna.startX * screenSize.width,
      dna.endX * screenSize.width,
      t,
    )!;
    final y = lerpDouble(
      dna.startY * screenSize.height,
      dna.endY * screenSize.height,
      t,
    )!;

    // 非均匀形态扭曲
    final scaleX =
        dna.scaleBase + (math.sin(t * math.pi * 2 + dna.phaseOffset) * 0.2);
    final scaleY =
        dna.scaleBase + (math.cos(t * math.pi * 2 + dna.phaseOffset) * 0.2);
    final rotation = t * math.pi * dna.rotationMultiplier;

    return Positioned(
      left: x - (baseSize / 2),
      top: y - (baseSize / 2),
      child: Transform(
        transform: Matrix4.identity()
          ..rotateZ(rotation)
          ..scale(scaleX, scaleY),
        alignment: Alignment.center,
        child: Container(
          width: baseSize,
          height: baseSize,
          decoration: BoxDecoration(shape: BoxShape.circle, color: dna.color),
        ),
      ),
    );
  }
}

/// 色块 DNA 模型
class _AuroraBlobDNA {
  final double startX, endX, startY, endY;
  final double scaleBase;
  final double rotationMultiplier;
  final double phaseOffset;
  final Color color;

  _AuroraBlobDNA({
    required this.startX,
    required this.endX,
    required this.startY,
    required this.endY,
    required this.scaleBase,
    required this.rotationMultiplier,
    required this.phaseOffset,
    required this.color,
  });
}

class _StarTrailPainter extends CustomPainter {
  final int elapsedMs;
  final List<_StarData> stars;

  static List<_StarData>? _cachedStars;

  _StarTrailPainter({required this.elapsedMs}) : stars = _getStars();

  static List<_StarData> _getStars() {
    if (_cachedStars != null) return _cachedStars!;
    final random = math.Random(42);
    _cachedStars = List.generate(300, (i) {
      final radiusFactor = random.nextDouble();
      final arcLen = 0.05 + random.nextDouble() * ((math.pi * 2 / 5) - 0.05);

      // 目标：30-60秒转一周 (弧度/毫秒)
      final periodSeconds = 30.0 + random.nextDouble() * 30.0;
      final angularSpeed = (2 * math.pi) / (periodSeconds * 1000.0);

      final initialAngle = random.nextDouble() * math.pi * 2;
      final opacity = 0.2 + random.nextDouble() * 0.5;
      final isWhite = random.nextDouble() > 0.1;
      final color = isWhite
          ? Colors.white.withOpacity(opacity)
          : const Color(0xFFB4DCFF).withOpacity(opacity * 0.6);
      final strokeWidth = (0.6 + random.nextDouble() * 1.0) * 2.0;

      return _StarData(
        radiusFactor: radiusFactor,
        arcLen: arcLen,
        angularSpeed: angularSpeed,
        initialAngle: initialAngle,
        color: color,
        strokeWidth: strokeWidth,
      );
    });
    return _cachedStars!;
  }

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width, size.height);
    final maxRadius = size.longestSide * 1.2;

    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;

    for (final star in stars) {
      final radius = star.radiusFactor * maxRadius;
      // 增加模运算，确保角度始终在 [0, 2π] 范围内，极致规避精度偏离
      final currentAngle =
          (star.initialAngle - (star.angularSpeed * elapsedMs)) % (math.pi * 2);

      paint.color = star.color;
      paint.strokeWidth = star.strokeWidth;

      canvas.drawArc(
        Rect.fromCircle(center: center, radius: radius),
        currentAngle,
        star.arcLen,
        false,
        paint,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _StarTrailPainter oldDelegate) => true;
}

class _StarData {
  final double radiusFactor;
  final double arcLen;
  final double angularSpeed;
  final double initialAngle;
  final Color color;
  final double strokeWidth;

  _StarData({
    required this.radiusFactor,
    required this.arcLen,
    required this.angularSpeed,
    required this.initialAngle,
    required this.color,
    required this.strokeWidth,
  });
}
