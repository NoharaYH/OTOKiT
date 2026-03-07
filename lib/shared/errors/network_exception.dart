import 'app_exception.dart';

/// 网络层异常：超时、连接失败、非 2xx 响应等。
/// 由 infrastructure/network 在捕获 DioException 后转换为此类型。
abstract class NetworkException extends AppException {
  const NetworkException(super.message, {super.cause});

  factory NetworkException.timeout({String message = '请求超时', dynamic cause}) =>
      _NetworkTimeout(message, cause: cause);

  factory NetworkException.connection({String message = '连接失败', dynamic cause}) =>
      _NetworkConnection(message, cause: cause);

  factory NetworkException.serverError(int? statusCode, {String? message, dynamic cause}) =>
      _NetworkServerError(statusCode, message ?? '服务器错误', cause: cause);
}

class _NetworkTimeout extends NetworkException {
  const _NetworkTimeout(super.message, {super.cause});
}

class _NetworkConnection extends NetworkException {
  const _NetworkConnection(super.message, {super.cause});
}

class _NetworkServerError extends NetworkException {
  const _NetworkServerError(this.statusCode, super.message, {super.cause});
  final int? statusCode;
}
