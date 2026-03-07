import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:crypto/crypto.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:injectable/injectable.dart';
import 'package:app_links/app_links.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../domain/entities/vpn_start_config.dart';
import '../../domain/entities/vpn_status.dart';
import '../../domain/repositories/vpn_repository.dart';
import '../../kernel/config/chunithm_config.dart';
import '../../kernel/config/endpoints.dart';
import '../../kernel/config/env.dart';
import '../../kernel/config/maimai_config.dart';
import '../../kernel/config/system_config.dart';
import '../../kernel/services/api_service.dart';
import '../../kernel/services/maimai_html_parser.dart';
import '../../kernel/services/storage_service.dart';
import '../../ui/design_system/constants/strings.dart';

/// TransferProvider：纯 UI 状态中转层。
@injectable
class TransferProvider extends ChangeNotifier {
  TransferProvider(
    this._apiService,
    this._storageService,
    this._vpnRepo,
  ) {
    _loadTokens();
    _statusSubscription = _vpnRepo.statusStream.listen(_onVpnStatus);
    _logSubscription = _vpnRepo.logStream.listen(_handleLog);
    _initDeepLinks();
  }

  final ApiService _apiService;
  final StorageService _storageService;
  final VpnRepository _vpnRepo;
  final _appLinks = AppLinks();
  StreamSubscription<Uri>? _linkSubscription;
  StreamSubscription<VpnStatus>? _statusSubscription;
  StreamSubscription<String>? _logSubscription;

  // 数据状态
  String dfToken = '';
  // 统一身份凭证：落雪 (LXNS) 账号全局唯一，不再按游戏隔离
  String lxnsToken = '';
  String? lxnsRefreshToken;
  bool _isLxnsOAuthDone = false;
  String? _pkceVerifier; // PKCE 原始校验码

  // UI 状态
  bool _isLoading = false;
  bool _isStorageLoaded = false;
  final Map<int, bool> _isDivingFishVerifiedMap = {};
  bool _isLxnsVerified = false;
  bool _isVpnRunning = false;
  bool _isTracking = false;
  bool _pendingWechat = false; // 等 VPN 真正启动后再跳微信
  int? _trackingGameType;
  Set<int> _currentDifficulties = {0, 1, 2, 3, 4, 5};
  String? _errorMessage;
  String? _successMessage;
  final Map<int, String> _gameLogs = {};

  // Getters (Legacy - primarily for back-compat or active tab)
  bool get isLoading => _isLoading;
  bool get isStorageLoaded => _isStorageLoaded;
  bool get isDivingFishVerified =>
      _isDivingFishVerifiedMap[_activeGameType] ?? false;
  bool get isLxnsVerified => _isLxnsVerified;
  bool get isVpnRunning => _isVpnRunning;
  bool get isTracking => _isTracking;
  int? get trackingGameType => _trackingGameType;
  String? get errorMessage => _errorMessage;
  String? get successMessage => _successMessage;
  String get vpnLog => _gameLogs[_trackingGameType] ?? "";
  String getVpnLog(int gameType) => _gameLogs[gameType] ?? "";
  bool get isLxnsOAuthDone => _isLxnsOAuthDone;

  bool isDivingFishVerifiedFor(int gameType) =>
      _isDivingFishVerifiedMap[gameType] ?? false;
  bool isLxnsVerifiedFor(int gameType) => _isLxnsVerified;
  bool isLxnsOAuthDoneFor(int gameType) => _isLxnsOAuthDone;
  String lxnsTokenFor(int gameType) => lxnsToken;

  // 当前选中的游戏类型（表单页中接收）
  int _activeGameType = 0;
  void setActiveGameType(int gameType) {
    _activeGameType = gameType;
    notifyListeners();
  }

  Timer? _logNotifyTimer;

  void _onVpnStatus(VpnStatus status) {
    _isVpnRunning = status.isRunning;
    if (status.statusText != null) _successMessage = status.statusText;
    if (status.isDone || status.statusText == UiStrings.syncFinish) {
      _isTracking = false;
      _vpnRepo.stop();
    }
    if (status.isRunning && _pendingWechat) {
      _pendingWechat = false;
      _afterVpnReady();
    }
    notifyListeners();
  }

  void _initDeepLinks() {
    _linkSubscription = _appLinks.uriLinkStream.listen((uri) async {
      debugPrint('[DEEPLINK] Incoming: $uri');
      // 统一入口: /oauth/callback
      if ((uri.scheme == 'https' || uri.scheme == 'otokit') &&
          uri.host == SystemConfig.oauthDeepLinkHost &&
          uri.path == SystemConfig.oauthCallbackPath) {
        final state = uri.queryParameters['state'];
        final code = uri.queryParameters['code'];

        // 逻辑分发：识别 tenant=lxns
        if (state != null && code != null) {
          final decoded = utf8.decode(base64Url.decode(state));
          if (decoded.contains('tenant=lxns')) {
            int gt = 0;
            if (decoded.contains('gameType=1')) gt = 1;
            await _handleLxnsOAuth(code, gameType: gt);
          }
        }
      }
    });
  }

  /// 发起落雪 OAuth 授权流程 (PKCE)
  /// [gameType]: 0 = maimai, 1 = chunithm
  Future<void> startLxnsOAuthFlow({int gameType = 0}) async {
    _pkceVerifier = _generateRandomString(128);
    final challenge = _computeChallenge(_pkceVerifier!);

    final state = base64Url.encode(
      utf8.encode(
        "tenant=lxns&gameType=$gameType&nonce=${_generateRandomString(8)}",
      ),
    );

    // 统一 OAuth Scope：LXNS 采用通用权限标识，涵盖所有关联游戏
    const String scope = SystemConfig.oauthScope;

    const int oauthPort = SystemConfig.oauthPort;
    const String redirectUri = SystemConfig.oauthRedirectUri;

    try {
      final server = await HttpServer.bind(
        InternetAddress.loopbackIPv4,
        oauthPort,
        shared: true,
      );
      server.listen((HttpRequest request) async {
        if (request.uri.path == SystemConfig.oauthCallbackPath) {
          final code = request.uri.queryParameters['code'];
          final stateParam = request.uri.queryParameters['state'];
          request.response
            ..statusCode = 200
            ..headers.contentType = ContentType.html
            ..write(
              '<meta charset="utf-8"><body><h2 style="text-align:center;margin-top:50px;">${UiStrings.oauthSuccess}！您可以关闭此网页并返回 ${UiStrings.appName}。</h2></body>',
            );
          await request.response.close();
          await server.close(force: true);

          if (code != null) {
            int gt = 0;
            if (stateParam != null) {
              final decoded = utf8.decode(base64Url.decode(stateParam));
              if (decoded.contains('gameType=1')) gt = 1;
            }
            await _handleLxnsOAuth(code, gameType: gt);
          }
        }
      });
      // 超时防护：5分钟后自动关闭监听
      Future.delayed(const Duration(minutes: 5), () {
        server.close(force: true);
      });
    } catch (e) {
      debugPrint("[OAuth] Server bind error: $e");
    }

    final url = Uri.parse(
      '${Endpoints.lxnsAuthorize}'
      '?client_id=${Env.lxnsClientId}'
      '&redirect_uri=${Uri.encodeComponent(redirectUri)}'
      '&response_type=code'
      '&scope=$scope'
      '&state=$state'
      '&code_challenge=$challenge'
      '&code_challenge_method=S256',
    );

    if (await canLaunchUrl(url)) {
      await launchUrl(url, mode: LaunchMode.externalApplication);
    } else {
      _errorMessage = UiStrings.errOAuthNoLaunch;
      notifyListeners();
    }
  }

  Future<void> _handleLxnsOAuth(String code, {int gameType = 0}) async {
    if (_pkceVerifier == null) {
      _errorMessage = UiStrings.errOAuthNoVerifier;
      notifyListeners();
      return;
    }

    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      final result = await _apiService.exchangeLxnsCode(
        code,
        Env.lxnsClientId,
        Env.lxnsClientSecret,
        _pkceVerifier!,
      );

      if (result != null) {
        final accessToken = result['access_token'] as String;
        final refreshToken = result['refresh_token'] as String?;

        lxnsToken = accessToken;
        lxnsRefreshToken = refreshToken;
        _isLxnsOAuthDone = true;
        _isLxnsVerified = true;

        _pkceVerifier = null;

        // 保存全局凭证（不再携带游戏类型后缀，或者统一使用主游戏 key）
        await _storageService.save(
          StorageService.kLxnsTokenPrefix,
          accessToken,
        );
        if (refreshToken != null) {
          await _storageService.save(
            StorageService.kLxnsRefreshTokenPrefix,
            refreshToken,
          );
        }
        _successMessage = UiStrings.oauthSuccess;
      } else {
        _errorMessage = UiStrings.oauthExchangeFailed;
      }
    } catch (e) {
      debugPrint('[OAuth] Token exchange exception: $e');
      _errorMessage = '[OAuth] 字符交换异常: $e';
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  // PKCE Helper Functions
  String _generateRandomString(int length) {
    const chars =
        'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~';
    final random = Random.secure();
    return List.generate(
      length,
      (index) => chars[random.nextInt(chars.length)],
    ).join();
  }

  String _computeChallenge(String verifier) {
    final bytes = ascii.encode(verifier);
    final digest = sha256.convert(bytes);
    return base64Url.encode(digest.bytes).replaceAll('=', '');
  }

  void _handleLog(String msg) async {
    if (_trackingGameType == null) return;

    // 拦截 HTML 原始数据，执行手机侧独立解析与上传
    if (msg.contains("[HTML_DATA_SYNC]")) {
      try {
        final rawJson = msg.split("[HTML_DATA_SYNC]")[1];
        final payload = jsonDecode(rawJson);
        final html = payload['html'] as String;
        final token = payload['token'] as String;
        final diff = payload['diff'] as int;
        final gameType = payload['gameType'] as int;

        if (gameType == 0) {
          // Maimai
          final records = MaimaiHtmlParser.parse(html);
          if (records.isNotEmpty) {
            final response = await _apiService.uploadMaimaiRecords(
              token,
              records,
            );
            if (response != null && response['message'] == '更新成功') {
              final label = (diff == 10) ? UiStrings.diffLabelUtage : "难度$diff";
              appendLog(
                "${UiStrings.logTagUpload} ${UiStrings.logUploadSuccess.replaceAll("{0}", UiStrings.modeDivingFish).replaceAll("{1}", label)}",
              );
              // Java 侧会负责打印最终的完毕日志，此处不再冗余重复
            } else {
              final label = (diff == 10) ? UiStrings.diffLabelUtage : "难度$diff";
              appendLog(
                "${UiStrings.logTagError} ${UiStrings.logErrUpload.replaceAll("{0}", UiStrings.modeDivingFish).replaceAll("{1}", label).replaceAll("{2}", "400").replaceAll("{3}", response?['message'] ?? '未知错误')}",
              );
            }
          }
          // 重要：反馈给原生侧，解除同步锁，允许切换至落雪平台
          await _vpnRepo.notifyDivingFishTaskDone();
        }
      } catch (e) {
        appendLog("${UiStrings.logTagError} ${UiStrings.logErrParse}: $e");
        await _vpnRepo.notifyDivingFishTaskDone();
      }
      return;
    }

    // 同时输出到 IDE 调试控制台，响应“控制台内部 print”要求
    print(msg);
    _gameLogs[_trackingGameType!] =
        "${_gameLogs[_trackingGameType!] ?? ""}$msg\n";

    // 对于关键操作标记，立即通知 UI 避免 100ms 防抖带来的视觉滞后
    if (msg.contains('[PAUSE]') ||
        msg.contains('[RESUME]') ||
        msg.contains('[START]')) {
      _logNotifyTimer?.cancel();
      notifyListeners();
      return;
    }

    // 普通日志保持防抖，避免高频重建
    if (_logNotifyTimer?.isActive ?? false) return;
    _logNotifyTimer = Timer(const Duration(milliseconds: 100), () {
      notifyListeners();
    });
  }

  /// 向控制台追加一行日志（UI 层调用，走防抖通知路径）。
  void appendLog(String msg) => _handleLog(msg);

  Future<void> startVpn({required int mode}) async {
    final finalDfToken = (mode == 0 || mode == 1) ? dfToken : "";
    final finalLxnsToken = (mode == 2 || mode == 1) ? lxnsToken : "";

    final String lxnsUploadPath = (_trackingGameType == 1)
        ? ChunithmConfig.lxnsUploadPath
        : MaimaiConfig.lxnsUploadPath;
    final String fullLxnsUploadUrl =
        "${Endpoints.lxnsBaseUrl}/$lxnsUploadPath";

    final String dfUploadPath = (_trackingGameType == 1)
        ? ChunithmConfig.dfUploadPath
        : MaimaiConfig.dfUploadPath;
    final String fullDfUploadUrl = "${Endpoints.dfBaseUrl}/$dfUploadPath";

    final String wahlapBaseUrl = (_trackingGameType == 1)
        ? ChunithmConfig.wahlapBase
        : MaimaiConfig.wahlapBase;
    final String wahlapAuthLabel = (_trackingGameType == 1)
        ? ChunithmConfig.wahlapAuthLabel
        : MaimaiConfig.wahlapAuthLabel;
    final String fullWahlapAuthUrl =
        "${Endpoints.wahlapAuthBaseUrl}$wahlapAuthLabel";

    final Map<int, String> fetchUrlMap = {};
    if (_trackingGameType == 0) {
      fetchUrlMap[-1] = "${wahlapBaseUrl}friend/userFriendCode/";
      fetchUrlMap[-2] = "${wahlapBaseUrl}record/";
      fetchUrlMap[10] =
          "${wahlapBaseUrl}record/musicGenre/search/?genre=99&diff=10";
      for (var d in _currentDifficulties) {
        if (d >= 0 && d != 10) {
          fetchUrlMap[d] =
              "${wahlapBaseUrl}record/musicSort/search/?search=V&sort=1&playCheck=on&diff=$d";
        }
      }
    } else {
      fetchUrlMap[-1] = "${wahlapBaseUrl}home/playerData";
      fetchUrlMap[-2] = "${wahlapBaseUrl}record/playlog";
      fetchUrlMap[5] = "${wahlapBaseUrl}record/worldsEndList";
      fetchUrlMap[10] = "${wahlapBaseUrl}record/worldsEndList";
      for (var d in _currentDifficulties) {
        if (d >= 0 && d < 5) {
          fetchUrlMap[d] = "${wahlapBaseUrl}record/musicGenre?difficulty=$d";
        }
      }
    }

    final List<String> genreList = (_trackingGameType == 1)
        ? ChunithmConfig.genreList
        : MaimaiConfig.genreList;

    final config = VpnStartConfig(
      dfToken: finalDfToken,
      lxnsToken: finalLxnsToken,
      lxnsUploadUrl: fullLxnsUploadUrl,
      dfUploadUrl: fullDfUploadUrl,
      wahlapBaseUrl: wahlapBaseUrl,
      wahlapAuthUrl: fullWahlapAuthUrl,
      genreList: genreList,
      fetchUrlMap: fetchUrlMap,
      gameTypeIndex: _trackingGameType,
      difficulties: _currentDifficulties.toList(),
    );

    await _vpnRepo.prepareAndStart(config);
    if (_pendingWechat) {
      _pendingWechat = false;
      await _afterVpnReady();
    }
  }

  /// VPN 实际启动后执行：写剪贴板、跳微信、打印日志。
  /// 由 startVpn 在两条路径（直接授权 / onVpnPrepared 回调）收口调用。
  Future<void> _afterVpnReady() async {
    final randomStr = DateTime.now().millisecondsSinceEpoch
        .toRadixString(36)
        .substring(0, 8);
    final localProxyUrl = "${SystemConfig.proxyBaseUrl}/$randomStr";
    await Clipboard.setData(ClipboardData(text: localProxyUrl));

    final wxUrl = Uri.parse("weixin://");
    if (await canLaunchUrl(wxUrl)) {
      await launchUrl(wxUrl, mode: LaunchMode.externalApplication);
    }

    appendLog("${UiStrings.logTagVpn} ${UiStrings.logVpnStarted}");
    appendLog("${UiStrings.logTagClipboard} ${UiStrings.logClipReady}");
    appendLog(UiStrings.logWaitLink);
  }

  Future<bool> stopVpn({
    bool resetState = true,
    bool isManually = false,
  }) async {
    if (isManually) {
      appendLog("${UiStrings.logTagSystem} ${UiStrings.logSysTerminated}");
    }
    await _vpnRepo.stop();
    if (resetState) {
      if (_trackingGameType != null && isManually) {
        // 手动终止时清理对应游戏的日志缓存，实现彻底隔离
        _gameLogs.remove(_trackingGameType);
      }
      _isTracking = false;
      _trackingGameType = null;
    }
    notifyListeners();
    return true;
  }

  void startTracking({required int gameType}) {
    _isTracking = true;
    _trackingGameType = gameType;
    notifyListeners();
  }

  void stopTracking() {
    _isTracking = false;
    _trackingGameType = null;
    notifyListeners();
  }

  Future<void> startImport({
    required int gameType,
    required int mode,
    Set<int> difficulties = const {0, 1, 2, 3, 4, 5},
  }) async {
    _isTracking = true;
    _trackingGameType = gameType;
    _currentDifficulties = difficulties;
    _gameLogs[gameType] = "";
    notifyListeners();

    appendLog("${UiStrings.logTagSystem} ${UiStrings.logSysStart}");
    appendLog("${UiStrings.logTagVpn} ${UiStrings.logVpnStarting}");

    try {
      _pendingWechat = true;
      await startVpn(mode: mode);
      // 到这里有两种情况：
      // 1. 已有 VPN 权限 → startVpn 内部已调用 _afterVpnReady，_pendingWechat=false
      // 2. 首次需要授权 → 系统弹窗未关闭，_pendingWechat 保持 true；
      //    用户点击允许后 onVpnPrepared 触发 startVpn()，届时再执行 _afterVpnReady
    } catch (e) {
      _pendingWechat = false;
      appendLog(
        "${UiStrings.logTagError} ${UiStrings.logErrVpnStart.replaceAll("{0}", e.toString())}",
      );
    }
  }

  @override
  void dispose() {
    _logNotifyTimer?.cancel();
    _linkSubscription?.cancel();
    _statusSubscription?.cancel();
    _logSubscription?.cancel();
    super.dispose();
  }

  Future<void> _loadTokens() async {
    await Future.delayed(const Duration(milliseconds: 300));
    final df = await _storageService.read(StorageService.kDivingFishToken);
    if (df != null && df.isNotEmpty) {
      dfToken = df;
      for (final gt in [0, 1]) {
        _isDivingFishVerifiedMap[gt] = true;
      }
    }

    // 加载全局唯一 LXNS Token
    final lxns = await _storageService.read(StorageService.kLxnsTokenPrefix);
    final refresh = await _storageService.read(
      StorageService.kLxnsRefreshTokenPrefix,
    );

    if (lxns != null && lxns.isNotEmpty) {
      lxnsToken = lxns;
      _isLxnsVerified = true;
    }
    if (refresh != null && refresh.isNotEmpty) {
      lxnsRefreshToken = refresh;
    }

    // Access Token 寿命 15 分钟，启动时若有 refresh_token 则静默续期
    if (lxnsRefreshToken != null && lxnsRefreshToken!.isNotEmpty) {
      try {
        final refreshed = await _apiService.refreshLxnsToken(
          lxnsRefreshToken!,
          Env.lxnsClientId,
          Env.lxnsClientSecret,
        );
        if (refreshed != null) {
          lxnsToken = refreshed['access_token'] ?? lxnsToken;
          lxnsRefreshToken = refreshed['refresh_token'] ?? lxnsRefreshToken;
          _isLxnsVerified = true;
          _isLxnsOAuthDone = true;
          await _storageService.save(
            StorageService.kLxnsTokenPrefix,
            lxnsToken,
          );
          await _storageService.save(
            StorageService.kLxnsRefreshTokenPrefix,
            lxnsRefreshToken!,
          );
        }
      } catch (_) {
        // 静默失败
      }
    }

    // 将当前激活游戏的 token 同步到公开字段
    _isStorageLoaded = true;
    notifyListeners();
  }

  void resetVerification({int? gameType, bool df = false, bool lxns = false}) {
    if (df) _isDivingFishVerifiedMap[gameType ?? _activeGameType] = false;
    if (lxns) _isLxnsVerified = false;
    _errorMessage = null;
    _successMessage = null;
    notifyListeners();
  }

  void updateTokens({int? gameType, String? df, String? lxns}) {
    if (df != null) {
      dfToken = df;
      for (final gt in [0, 1]) {
        _isDivingFishVerifiedMap[gt] = false;
      }
    }
    if (lxns != null) {
      lxnsToken = lxns;
      _isLxnsVerified = false;
      _isLxnsOAuthDone = false;

      _storageService.save(StorageService.kLxnsTokenPrefix, lxns);
    }
    notifyListeners();
  }

  Future<bool> verifyAndSave({required int mode, required int gameType}) async {
    _isLoading = true;
    _errorMessage = null;
    _successMessage = null;
    notifyListeners();

    await Future.delayed(const Duration(milliseconds: 500));
    final needsDf = mode == 0 || mode == 1;
    final needsLxns = mode == 2 || mode == 1;

    if (needsDf && dfToken.isEmpty) {
      _errorMessage = UiStrings.inputDivingFishToken;
      _isLoading = false;
      notifyListeners();
      return false;
    }
    if (needsLxns && lxnsToken.isEmpty) {
      _errorMessage = UiStrings.inputLxnsToken;
      _isLoading = false;
      notifyListeners();
      return false;
    }

    try {
      bool dfSuccess = _isDivingFishVerifiedMap[gameType] ?? false;
      bool lxnsSuccess = _isLxnsVerified;

      if (needsDf && !dfSuccess) {
        dfSuccess = await _apiService.validateDivingFishToken(dfToken);
        if (!dfSuccess) {
          _errorMessage =
              "${UiStrings.modeDivingFish} ${UiStrings.logTagAuth} 验证失败";
          _isLoading = false;
          notifyListeners();
          return false;
        }
      }
      if (needsLxns && !lxnsSuccess) {
        lxnsSuccess = await _apiService.validateLxnsToken(
          lxnsToken,
          gameType: gameType,
        );
        if (!lxnsSuccess) {
          _errorMessage = "${UiStrings.modeLxns} ${UiStrings.logTagAuth} 验证失败";
          _isLoading = false;
          notifyListeners();
          return false;
        }
      }

      _isDivingFishVerifiedMap[gameType] = dfSuccess;
      _isLxnsVerified = lxnsSuccess;

      if (dfSuccess) {
        await _storageService.save(StorageService.kDivingFishToken, dfToken);
      }
      if (lxnsSuccess) {
        await _storageService.save(StorageService.kLxnsTokenPrefix, lxnsToken);
      }

      _successMessage = UiStrings.verifySuccess;
      _isLoading = false;
      notifyListeners();
      return true;
    } catch (e) {
      _errorMessage = "验证过程发生错误: $e";
      _isLoading = false;
      notifyListeners();
      return false;
    }
  }
}
