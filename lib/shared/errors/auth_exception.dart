import 'app_exception.dart';

/// 认证相关异常：Token 无效、OAuth 兑换失败、缺少校验码等。
/// 由 infrastructure 或 domain usecase 使用，禁止 DioException 泄漏到上层。
abstract class AuthException extends AppException {
  const AuthException(super.message, {super.cause});

  factory AuthException.missingDivingFishToken() =>
      const _AuthExceptionImpl('缺少 DivingFish Token');

  factory AuthException.missingLxnsToken() =>
      const _AuthExceptionImpl('缺少 LXNS Token');

  factory AuthException.exchangeFailed({String? message, dynamic cause}) =>
      _AuthExceptionImpl(message ?? 'OAuth 授权码兑换失败', cause: cause);

  factory AuthException.unauthorized({String? message, dynamic cause}) =>
      _AuthExceptionImpl(message ?? '未授权', cause: cause);

  factory AuthException.network(String message, {dynamic cause}) =>
      _AuthExceptionImpl(message, cause: cause);
}

final class _AuthExceptionImpl extends AuthException {
  const _AuthExceptionImpl(super.message, {super.cause});
}
