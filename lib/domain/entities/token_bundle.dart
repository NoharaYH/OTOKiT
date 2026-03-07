/// 认证凭证聚合，供 AuthRepository 与 UseCase 使用。
/// 纯 Dart，无框架依赖。
class TokenBundle {
  const TokenBundle({
    this.dfToken = '',
    this.lxnsToken = '',
    this.lxnsRefreshToken,
  });

  final String dfToken;
  final String lxnsToken;
  final String? lxnsRefreshToken;

  bool get hasDivingFish => dfToken.isNotEmpty;
  bool get hasLxns => lxnsToken.isNotEmpty;
  bool get canRefresh => lxnsRefreshToken?.isNotEmpty == true;
}
