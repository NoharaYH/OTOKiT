/// 应用层异常基类，供各层统一错误类型使用。
/// 禁止将框架异常（如 DioException）直接泄漏到 domain/application。
abstract class AppException implements Exception {
  const AppException(this.message, {this.cause});

  final String message;
  final dynamic cause;

  @override
  String toString() => 'AppException: $message${cause != null ? ' (cause: $cause)' : ''}';
}
