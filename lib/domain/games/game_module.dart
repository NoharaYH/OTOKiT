import '../entities/token_bundle.dart';
import '../entities/vpn_start_config.dart';
import '../repositories/music_library_repository.dart';
import '../value_objects/difficulty_set.dart';
import '../value_objects/transfer_mode.dart';

/// 多游戏扩展抽象：每个游戏实现此接口，上层通过 GameRegistry 获取。
/// 传分 URL 拼装、HTML 解析、曲库仓储由各游戏 Module 提供。
abstract class GameModule {
  /// 游戏唯一标识，与 GameType.gameId 一致
  String get gameId;

  /// UI 展示名称
  String get displayName;

  /// 对应主题域 ID（对应 AppTheme.themeDomainId）
  String get themeDomainId;

  /// 构造 VPN 启动配置（替代 TransferProvider 内 URL 拼装）
  VpnStartConfig buildVpnConfig({
    required TokenBundle tokens,
    required TransferMode mode,
    required DifficultySet difficulties,
  });

  /// HTML 解析（委托给 infra 层的具体实现）
  List<Map<String, dynamic>> parseHtmlRecords(String html, int difficulty);

  /// LXNS 平台上传地址
  String lxnsUploadUrl(String baseUrl);

  /// DivingFish 平台上传地址
  String divingFishUploadUrl(String baseUrl);

  /// 此游戏的曲库仓储（由 DI 注入）
  MusicLibraryRepository get musicLibraryRepository;
}
