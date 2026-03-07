import 'package:dio/dio.dart';
import 'package:injectable/injectable.dart';

import '../../../shared/env/app_env.dart';
import '../../../domain/value_objects/game_type.dart';
import '../../../shared/errors/auth_exception.dart';
import '../../../shared/result/result.dart';
import '../dto/token_response_dto.dart';

@lazySingleton
class LxnsApiClient {
  LxnsApiClient(this._dio, this._env);

  final Dio _dio;
  final AppEnv _env;

  Future<Result<TokenResponseDto, AuthException>> exchangeCode(
    String code,
    String codeVerifier,
  ) async {
    try {
      final response = await _dio.post(
        _env.lxnsTokenExchangeUrl,
        data: {
          'grant_type': 'authorization_code',
          'client_id': _env.lxnsClientId,
          'code': code,
          'code_verifier': codeVerifier,
          'redirect_uri': _env.oauthRedirectUri,
        },
      );
      if (response.statusCode == 200) {
        final body = response.data;
        if (body is Map<String, dynamic> && body['data'] is Map) {
          final data = body['data'] as Map<String, dynamic>;
          return Result.success(TokenResponseDto.fromJson(data));
        }
      }
      return Result.failure(AuthException.exchangeFailed());
    } on DioException catch (e) {
      return Result.failure(
        AuthException.network(e.message ?? '网络错误', cause: e),
      );
    }
  }

  Future<Result<TokenResponseDto, AuthException>> refreshToken(
    String refreshToken,
  ) async {
    try {
      final response = await _dio.post(
        _env.lxnsTokenExchangeUrl,
        data: {
          'grant_type': 'refresh_token',
          'client_id': _env.lxnsClientId,
          'client_secret': _env.lxnsClientSecret,
          'refresh_token': refreshToken,
        },
      );
      if (response.statusCode == 200) {
        final body = response.data;
        if (body is Map<String, dynamic> && body['data'] is Map) {
          final data = body['data'] as Map<String, dynamic>;
          return Result.success(TokenResponseDto.fromJson(data));
        }
      }
      return Result.failure(AuthException.exchangeFailed());
    } on DioException catch (e) {
      return Result.failure(
        AuthException.network(e.message ?? '网络错误', cause: e),
      );
    }
  }

  Future<Result<void, AuthException>> validateToken(
    String token,
    GameType game,
  ) async {
    try {
      final playerPath = game == GameType.maimai
          ? 'user/maimai/player'
          : 'user/chunithm/player';
      final response = await _dio.get(
        '${_env.lxnsBaseUrl}/$playerPath',
        options: Options(
          headers: {'Authorization': 'Bearer $token'},
          receiveTimeout: const Duration(seconds: 10),
        ),
      );
      if (response.statusCode == 200) {
        final body = response.data;
        if (body is Map<String, dynamic> && body['success'] == true) {
          return Result.success(null);
        }
      }
      return Result.failure(AuthException.unauthorized());
    } on DioException catch (e) {
      return Result.failure(
        AuthException.network(e.message ?? '网络错误', cause: e),
      );
    }
  }
}
