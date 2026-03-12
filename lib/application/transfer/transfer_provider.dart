import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:injectable/injectable.dart';
import 'package:app_links/app_links.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../domain/entities/handle_log_result.dart';
import '../../domain/entities/token_bundle.dart';
import '../../domain/entities/vpn_status.dart';
import '../../domain/repositories/auth_repository.dart';
import '../../domain/repositories/vpn_repository.dart';
import '../../domain/usecases/transfer/handle_native_log_usecase.dart';
import '../../domain/usecases/transfer/refresh_lxns_token_usecase.dart';
import '../../domain/usecases/transfer/start_import_usecase.dart';
import '../../domain/usecases/transfer/stop_import_usecase.dart';
import '../../domain/usecases/transfer/verify_tokens_usecase.dart';
import '../../domain/value_objects/difficulty_set.dart';
import '../../domain/value_objects/game_type.dart';
import '../../domain/value_objects/transfer_mode.dart';
import '../../infrastructure/network/oauth/pkce_helper.dart';
import '../../shared/constants/domain_constants.dart';
import '../../shared/env/app_env.dart';

/// TransferController：纯 UI 状态中转层，通过 UseCase 编排业务。
/// Phase 4 命名规范，≤200 行目标见 02_状态层。
@injectable
class TransferController extends ChangeNotifier {
  TransferController(
    this._verifyTokens,
    this._startImport,
    this._stopImport,
    this._handleLogUsecase,
    this._refreshToken,
    this._authRepo,
    this._vpnRepo,
    this._env,
  ) {
    _loadTokens();
    _statusSubscription = _vpnRepo.statusStream.listen(_onVpnStatus);
    _logSubscription = _vpnRepo.logStream.listen(_handleLog);
    _initDeepLinks();
  }

  final VerifyTokensUsecase _verifyTokens;
  final StartImportUsecase _startImport;
  final StopImportUsecase _stopImport;
  final HandleNativeLogUsecase _handleLogUsecase;
  final RefreshLxnsTokenUsecase _refreshToken;
  final AuthRepository _authRepo;
  final VpnRepository _vpnRepo;
  final AppEnv _env;
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
    if (status.isDone || status.statusText == DomainConstants.syncFinish) {
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
    final deepLinkHost = Uri.parse(_env.oauthRedirectUriDeepLink).host;
    _linkSubscription = _appLinks.uriLinkStream.listen((uri) async {
      debugPrint('[DEEPLINK] Incoming: $uri');
      final isOAuthCallback = uri.path == _env.oauthCallbackPath &&
          ((uri.scheme == 'https' && uri.host == _env.oauthDeepLinkHost) ||
              (uri.scheme == 'otokit' &&
                  (uri.host == _env.oauthDeepLinkHost ||
                      uri.host == deepLinkHost)));
      if (isOAuthCallback) {
        final state = uri.queryParameters['state'];
        final code = uri.queryParameters['code'];
        if (state != null && code != null) {
          final decoded = utf8.decode(base64Url.decode(state));
          if (decoded.contains('tenant=lxns')) {
            int gt = 0;
            if (decoded.contains('gameType=1')) gt = 1;
            final redirectUri = uri.scheme == 'otokit' && uri.host == deepLinkHost
                ? _env.oauthRedirectUriDeepLink
                : _env.oauthRedirectUri;
            await _handleLxnsOAuth(code, gameType: gt, redirectUri: redirectUri);
          }
        }
      }
    });
  }

  /// 发起落雪 OAuth 授权流程 (PKCE)
  /// [gameType]: 0 = maimai, 1 = chunithm
  /// Android 使用 deep link 回调以从第三方浏览器唤起应用；桌面使用本地 HTTP 服务。
  Future<void> startLxnsOAuthFlow({int gameType = 0}) async {
    _pkceVerifier = PkceHelper.generateVerifier();
    final challenge = PkceHelper.computeChallenge(_pkceVerifier!);

    final state = base64Url.encode(
      utf8.encode(
        "tenant=lxns&gameType=$gameType&nonce=${PkceHelper.generateVerifier(length: 8)}",
      ),
    );

    final scope = _env.oauthScope;
    final isAndroid = Platform.isAndroid;
    final redirectUri =
        isAndroid ? _env.oauthRedirectUriDeepLink : _env.oauthRedirectUri;

    if (!isAndroid) {
      try {
        final oauthPort = _env.oauthPort;
        final server = await HttpServer.bind(
          InternetAddress.loopbackIPv4,
          oauthPort,
          shared: true,
        );
        server.listen((HttpRequest request) async {
          if (request.uri.path == _env.oauthCallbackPath) {
            final code = request.uri.queryParameters['code'];
            final stateParam = request.uri.queryParameters['state'];
            request.response
              ..statusCode = 200
              ..headers.contentType = ContentType.html
              ..write(
                '<meta charset="utf-8"><body><h2 style="text-align:center;margin-top:50px;">${DomainConstants.oauthSuccess}！您可以关闭此网页并返回 ${DomainConstants.appName}。</h2></body>',
              );
            await request.response.close();
            await server.close(force: true);

            if (code != null) {
              int gt = 0;
              if (stateParam != null) {
                final decoded =
                    utf8.decode(base64Url.decode(stateParam));
                if (decoded.contains('gameType=1')) gt = 1;
              }
              await _handleLxnsOAuth(
                code,
                gameType: gt,
                redirectUri: _env.oauthRedirectUri,
              );
            }
          }
        });
        Future.delayed(const Duration(minutes: 5), () {
          server.close(force: true);
        });
      } catch (e) {
        debugPrint("[OAuth] Server bind error: $e");
      }
    }

    final url = Uri.parse(
      '${_env.lxnsAuthorizeUrl}'
      '?client_id=${_env.lxnsClientId}'
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
      _errorMessage = DomainConstants.errOAuthNoLaunch;
      notifyListeners();
    }
  }

  Future<void> _handleLxnsOAuth(
    String code, {
    int gameType = 0,
    required String redirectUri,
  }) async {
    if (_pkceVerifier == null) {
      _errorMessage = DomainConstants.errOAuthNoVerifier;
      notifyListeners();
      return;
    }

    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      final result = await _authRepo.exchangeLxnsCode(
        code,
        _pkceVerifier!,
        redirectUri: redirectUri,
      );
      result.fold(
        (bundle) {
          lxnsToken = bundle.lxnsToken;
          lxnsRefreshToken = bundle.lxnsRefreshToken;
          _isLxnsOAuthDone = true;
          _isLxnsVerified = true;
          _pkceVerifier = null;
          _successMessage = DomainConstants.oauthSuccess;
        },
        (e) {
          _errorMessage = DomainConstants.oauthExchangeFailed;
        },
      );
    } catch (e) {
      debugPrint('[OAuth] Token exchange exception: $e');
      _errorMessage = '[OAuth] 字符交换异常: $e';
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  void _handleLog(String msg) async {
    if (_trackingGameType == null) return;

    // 拦截 HTML 原始数据，委托 HandleNativeLogUsecase 解析与上传
    if (msg.contains("[HTML_DATA_SYNC]")) {
      try {
        final game = _trackingGameType == 1
            ? GameType.chunithm
            : GameType.maimai;
        final result = await _handleLogUsecase.execute(msg, game);
        if (result is HandleLogResultUpload) {
          appendLog(
            "${DomainConstants.logTagUpload} ${result.message}",
          );
        } else if (result is HandleLogResultPlain && result.rawLog.isNotEmpty) {
          appendLog("${DomainConstants.logTagError} ${DomainConstants.logErrParse}");
        }
      } catch (e) {
        appendLog("${DomainConstants.logTagError} ${DomainConstants.logErrParse}: $e");
      }
      return;
    }

    // 同时输出到 IDE 调试控制台
    debugPrint(msg);
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
    final gameType = _trackingGameType ?? 0;
    final game = gameType == 1 ? GameType.chunithm : GameType.maimai;
    final modeEnum = _intToTransferMode(mode);
    final tokens = TokenBundle(
      dfToken: dfToken,
      lxnsToken: lxnsToken,
      lxnsRefreshToken: lxnsRefreshToken,
    );
    final difficulties = DifficultySet(_currentDifficulties);

    await _startImport.execute(
      game: game,
      mode: modeEnum,
      difficulties: difficulties,
      tokens: tokens,
    );
    if (_pendingWechat) {
      _pendingWechat = false;
      await _afterVpnReady();
    }
  }

  TransferMode _intToTransferMode(int mode) {
    return switch (mode) {
      0 => TransferMode.divingFishOnly,
      1 => TransferMode.both,
      2 => TransferMode.lxnsOnly,
      _ => TransferMode.divingFishOnly,
    };
  }

  /// VPN 实际启动后执行：写剪贴板、跳微信、打印日志。
  /// 由 startVpn 在两条路径（直接授权 / onVpnPrepared 回调）收口调用。
  Future<void> _afterVpnReady() async {
    final randomStr = DateTime.now().millisecondsSinceEpoch
        .toRadixString(36)
        .substring(0, 8);
    final localProxyUrl = "${_env.proxyBaseUrl}/$randomStr";
    await Clipboard.setData(ClipboardData(text: localProxyUrl));

    final wxUrl = Uri.parse("weixin://");
    if (await canLaunchUrl(wxUrl)) {
      await launchUrl(wxUrl, mode: LaunchMode.externalApplication);
    }

    appendLog("${DomainConstants.logTagVpn} ${DomainConstants.logVpnStarted}");
    appendLog("${DomainConstants.logTagClipboard} ${DomainConstants.logClipReady}");
    appendLog(DomainConstants.logWaitLink);
  }

  Future<bool> stopVpn({
    bool resetState = true,
    bool isManually = false,
  }) async {
    if (isManually) {
      appendLog("${DomainConstants.logTagSystem} ${DomainConstants.logSysTerminated}");
    }
    await _stopImport.execute();
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

    appendLog("${DomainConstants.logTagSystem} ${DomainConstants.logSysStart}");
    appendLog("${DomainConstants.logTagVpn} ${DomainConstants.logVpnStarting}");

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
        "${DomainConstants.logTagError} ${DomainConstants.logErrVpnStart.replaceAll("{0}", e.toString())}",
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
    final bundle = await _authRepo.loadTokenBundle();

    if (bundle.dfToken.isNotEmpty) {
      dfToken = bundle.dfToken;
      for (final gt in [0, 1]) {
        _isDivingFishVerifiedMap[gt] = true;
      }
    }
    if (bundle.lxnsToken.isNotEmpty) {
      lxnsToken = bundle.lxnsToken;
      _isLxnsVerified = true;
    }
    if (bundle.lxnsRefreshToken != null && bundle.lxnsRefreshToken!.isNotEmpty) {
      lxnsRefreshToken = bundle.lxnsRefreshToken;
    }

    if (bundle.canRefresh) {
      final result = await _refreshToken.execute(bundle);
      result.fold(
        (newBundle) {
          lxnsToken = newBundle.lxnsToken;
          lxnsRefreshToken = newBundle.lxnsRefreshToken;
          _isLxnsVerified = true;
          _isLxnsOAuthDone = true;
        },
        (_) {},
      );
    }

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
    bool needsSave = false;
    if (df != null) {
      dfToken = df;
      _isDivingFishVerifiedMap[gameType ?? _activeGameType] = false;
      needsSave = true;
    }
    if (lxns != null) {
      lxnsToken = lxns;
      _isLxnsVerified = false;
      _isLxnsOAuthDone = false;
      needsSave = true;
    }
    if (needsSave) {
      _authRepo.saveTokenBundle(TokenBundle(
        dfToken: dfToken,
        lxnsToken: lxnsToken,
        lxnsRefreshToken: lxnsRefreshToken,
      ));
    }
    notifyListeners();
  }

  Future<bool> verifyAndSave({required int mode, required int gameType}) async {
    _isLoading = true;
    _errorMessage = null;
    _successMessage = null;
    notifyListeners();

    await Future.delayed(const Duration(milliseconds: 500));
    final modeEnum = _intToTransferMode(mode);
    final game = gameType == 1 ? GameType.chunithm : GameType.maimai;
    final current = TokenBundle(
      dfToken: dfToken,
      lxnsToken: lxnsToken,
      lxnsRefreshToken: lxnsRefreshToken,
    );

    final result = await _verifyTokens.execute(current, modeEnum, game);
    return result.fold(
      (bundle) {
        dfToken = bundle.dfToken;
        lxnsToken = bundle.lxnsToken;
        lxnsRefreshToken = bundle.lxnsRefreshToken;
        _isDivingFishVerifiedMap[gameType] = true;
        _isLxnsVerified = modeEnum.needsLxns;
        _successMessage = DomainConstants.verifySuccess;
        _isLoading = false;
        notifyListeners();
        return true;
      },
      (e) {
        _errorMessage = e.message;
        _isLoading = false;
        notifyListeners();
        return false;
      },
    );
  }
}

/// 兼容旧引用，UI 可继续使用 TransferProvider 直到完成迁移。
typedef TransferProvider = TransferController;
