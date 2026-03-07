/// 基础设施静态常量，仅允许 infrastructure/ 与 bootstrap/ 引用。
/// 从 kernel/config 迁出，避免 application 层引用。
class InfraConstants {
  InfraConstants._();

  // Method Channel
  static const String vpnChannelName = 'com.noharayh.otokit/vpn';

  // OAuth
  static const int oauthPort = 34125;
  static const String oauthCallbackPath = '/oauth/callback';
  static const String oauthRedirectUri =
      'http://127.0.0.1:$oauthPort$oauthCallbackPath';
  static const String oauthDeepLinkHost = 'app.otokit.com';
  static const String oauthScope =
      'read_user_profile+read_player+write_player+read_user_token';

  // Proxy
  static const String proxyBaseUrl = 'http://127.0.0.2:8284';
}
