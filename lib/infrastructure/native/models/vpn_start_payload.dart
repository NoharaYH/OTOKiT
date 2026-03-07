import '../../../domain/entities/vpn_start_config.dart';

/// 下发给原生 VPN 的协议对象，仅做序列化。
class VpnStartPayload {
  VpnStartPayload._({
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
  final int? gameTypeIndex;
  final List<int> difficulties;

  factory VpnStartPayload.fromConfig(VpnStartConfig config) {
    return VpnStartPayload._(
      dfToken: config.dfToken,
      lxnsToken: config.lxnsToken,
      lxnsUploadUrl: config.lxnsUploadUrl,
      dfUploadUrl: config.dfUploadUrl,
      wahlapBaseUrl: config.wahlapBaseUrl,
      wahlapAuthUrl: config.wahlapAuthUrl,
      genreList: config.genreList,
      fetchUrlMap: config.fetchUrlMap,
      gameTypeIndex: config.gameTypeIndex,
      difficulties: config.difficulties,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'username': dfToken,
      'password': lxnsToken,
      'lxnsUploadUrl': lxnsUploadUrl,
      'dfUploadUrl': dfUploadUrl,
      'wahlapBaseUrl': wahlapBaseUrl,
      'wahlapAuthUrl': wahlapAuthUrl,
      'genreList': genreList,
      'fetchUrlMap': fetchUrlMap.map((k, v) => MapEntry(k.toString(), v)),
      'gameType': gameTypeIndex,
      'difficulties': difficulties,
    };
  }
}
