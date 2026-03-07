/// VPN 启动配置，由 GameModule.buildVpnConfig 产出，交给 VpnRepository.prepareAndStart。
/// 与原生侧协议字段一致，序列化由 infrastructure 负责。
class VpnStartConfig {
  const VpnStartConfig({
    required this.dfToken,
    required this.lxnsToken,
    required this.lxnsUploadUrl,
    required this.dfUploadUrl,
    required this.wahlapBaseUrl,
    required this.wahlapAuthUrl,
    required this.genreList,
    required this.fetchUrlMap,
    required this.gameTypeIndex,
    required this.difficulties,
  });

  final String dfToken;
  final String lxnsToken;
  final String lxnsUploadUrl;
  final String dfUploadUrl;
  final String wahlapBaseUrl;
  final String wahlapAuthUrl;
  final List<String> genreList;
  final Map<int, String> fetchUrlMap;
  /// 0 = maimai, 1 = chunithm
  final int? gameTypeIndex;
  final List<int> difficulties;
}
