import '../../../domain/entities/token_bundle.dart';

/// LXNS OAuth Token 响应 DTO，禁止泄漏裸 Map 到上层。
class TokenResponseDto {
  const TokenResponseDto({
    required this.accessToken,
    this.refreshToken,
    this.expiresIn,
  });

  final String accessToken;
  final String? refreshToken;
  final int? expiresIn;

  factory TokenResponseDto.fromJson(Map<String, dynamic> json) {
    return TokenResponseDto(
      accessToken: json['access_token'] as String,
      refreshToken: json['refresh_token'] as String?,
      expiresIn: json['expires_in'] as int?,
    );
  }

  TokenBundle toTokenBundle(TokenBundle existing) => TokenBundle(
        dfToken: existing.dfToken,
        lxnsToken: accessToken,
        lxnsRefreshToken: refreshToken,
      );
}
