import 'package:flutter/material.dart';
import '../../core/app_theme.dart';
import '../../../constants/assets.dart';

@GameTheme()
class CircleTheme extends AppTheme {
  const CircleTheme();

  @override
  ThemeDomain get domain => ThemeDomain.maimai;

  @override
  String get themeTitle => 'Circle';

  @override
  String get themeId => 'mai_circle';

  @override
  Color get light => const Color.fromARGB(255, 255, 226, 234);

  @override
  Color get basic => const Color.fromARGB(255, 255, 84, 138);

  // 严格遵循兜底规范，如果需要特定暗色可返回，但如果是文字色可能会被外部强制转为 #2d2d2d，
  // 视具体的渲染处而定，或者统一约束为不超过 #2d2d2d 的色域。
  @override
  Color get dark => const Color(0xFF333333);

  @override
  Color get subtitleColor => basic;

  @override
  Color get dotColor => basic;

  @override
  Widget buildBackground(BuildContext context) {
    return Stack(
      fit: StackFit.expand,
      children: [
        Container(
          decoration: BoxDecoration(
            gradient: RadialGradient(
              center: Alignment.center,
              radius: 1.0,
              // 背景渐变使用品牌固定色，不受 dark/light token 影响
              colors: [const Color(0xFFFFB7CD), const Color(0xFFEF096D)],
            ),
          ),
        ),
        const _RotatingImage(
          assetPath: AppAssets.maimaiBgPattern,
          duration: Duration(seconds: 240),
          scale: 3.5,
        ),
        const _RotatingImage(
          assetPath: AppAssets.maimaiCircleWhite,
          duration: Duration(seconds: 180),
          scale: 1.4,
          reverse: true,
        ),
        const _RotatingImage(
          assetPath: AppAssets.maimaiCircleYellow,
          duration: Duration(seconds: 280),
          scale: 1.7,
        ),
        const _RotatingImage(
          assetPath: AppAssets.maimaiCircleColorful,
          duration: Duration(seconds: 310),
          scale: 1.7,
          reverse: true,
        ),
        Positioned(
          top: 0,
          left: 0,
          child: Image.asset(
            AppAssets.maimaiTopLeft,
            width: 200,
            fit: BoxFit.contain,
          ),
        ),
        Positioned(
          top: 0,
          right: 0,
          child: Image.asset(
            AppAssets.maimaiTopRight,
            width: MediaQuery.of(context).size.width * 0.4,
            fit: BoxFit.contain,
          ),
        ),
        Positioned(
          bottom: 0,
          left: 0,
          child: Image.asset(
            AppAssets.maimaiBottomLeft,
            width: MediaQuery.of(context).size.width * 0.4,
            fit: BoxFit.contain,
          ),
        ),
        Positioned(
          bottom: 0,
          right: 0,
          child: Image.asset(
            AppAssets.maimaiBottomRight,
            width: 200,
            fit: BoxFit.contain,
          ),
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
    // 强制色阶判定
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

class _RotatingImage extends StatefulWidget {
  final String assetPath;
  final Duration duration;
  final double scale;
  final bool reverse;

  const _RotatingImage({
    required this.assetPath,
    required this.duration,
    this.scale = 1.0,
    this.reverse = false,
  });

  @override
  State<_RotatingImage> createState() => _RotatingImageState();
}

class _RotatingImageState extends State<_RotatingImage>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: widget.duration)
      ..repeat();
  }

  @override
  void didUpdateWidget(_RotatingImage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.duration != widget.duration) {
      _controller.duration = widget.duration;
      _controller.repeat();
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Positioned.fill(
      child: Center(
        child: AnimatedBuilder(
          animation: _controller,
          builder: (context, child) {
            final double angle = _controller.value * 2 * 3.14159;
            return Transform.rotate(
              angle: widget.reverse ? -angle : angle,
              child: Transform.scale(scale: widget.scale, child: child!),
            );
          },
          child: Image.asset(widget.assetPath, fit: BoxFit.contain),
        ),
      ),
    );
  }
}
