import 'package:injectable/injectable.dart';

import '../../shared/env/app_env.dart';
import 'endpoints.dart';
import 'env.dart';
import 'system_config.dart';

@lazySingleton
class ProdEnv implements AppEnv {
  const ProdEnv();

  @override
  String get lxnsClientId => Env.lxnsClientId;

  @override
  String get lxnsClientSecret => Env.lxnsClientSecret;

  @override
  int get oauthPort => SystemConfig.oauthPort;

  @override
  String get oauthCallbackPath => SystemConfig.oauthCallbackPath;

  @override
  String get oauthRedirectUri => SystemConfig.oauthRedirectUri;

  @override
  String get lxnsBaseUrl => Endpoints.lxnsBaseUrl;

  @override
  String get lxnsAuthorizeUrl => Endpoints.lxnsAuthorize;

  @override
  String get lxnsTokenExchangeUrl => Endpoints.lxnsTokenExchange;

  @override
  String get divingFishBaseUrl => Endpoints.dfBaseUrl;

  @override
  String get wahlapAuthBaseUrl => Endpoints.wahlapAuthBaseUrl;
}
