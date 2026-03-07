import 'package:dio/dio.dart';
import 'package:injectable/injectable.dart';

import '../../../shared/env/app_env.dart';
import '../../../shared/errors/auth_exception.dart';
import '../../../shared/errors/network_exception.dart';
import '../../../shared/result/result.dart';

@lazySingleton
class DivingFishApiClient {
  DivingFishApiClient(this._dio, this._env);

  final Dio _dio;
  final AppEnv _env;

  static const _recordsPath = 'maimaidxprober/player/records';
  static const _uploadPath = 'maimaidxprober/player/update_records';

  Future<Result<void, AuthException>> validateToken(String token) async {
    try {
      final response = await _dio.get(
        '${_env.divingFishBaseUrl}/$_recordsPath',
        options: Options(
          headers: {'Import-Token': token},
          receiveTimeout: const Duration(seconds: 10),
        ),
      );
      return response.statusCode == 200
          ? Result.success(null)
          : Result.failure(AuthException.unauthorized());
    } on DioException catch (e) {
      return Result.failure(
        AuthException.network(e.message ?? '网络错误', cause: e),
      );
    }
  }

  Future<Result<void, NetworkException>> uploadMaimaiRecords(
    String token,
    List<Map<String, dynamic>> records,
  ) async {
    try {
      final response = await _dio.post(
        '${_env.divingFishBaseUrl}/$_uploadPath',
        data: records,
        options: Options(
          headers: {'Import-Token': token},
          contentType: 'application/json',
          receiveTimeout: const Duration(seconds: 30),
        ),
      );
      if (response.statusCode == 200) {
        return Result.success(null);
      }
      final body = response.data;
      final message = body is Map ? body['message'] as String? : null;
      return Result.failure(NetworkException.serverError(
        response.statusCode,
        message: message ?? '上传失败',
      ));
    } on DioException catch (e) {
      return Result.failure(NetworkException.serverError(
        e.response?.statusCode,
        message: e.message ?? '上传失败',
        cause: e,
      ));
    }
  }
}
