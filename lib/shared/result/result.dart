/// 表示可能失败的操作结果，供 UseCase 与 Controller 统一返回类型。
/// 禁止在 domain 层直接抛异常；通过 Result 传递错误。
sealed class Result<T, E> {
  const Result();

  factory Result.success(T value) => Success<T, E>(value);
  factory Result.failure(E error) => Failure<T, E>(error);

  bool get isSuccess => this is Success<T, E>;
  bool get isFailure => this is Failure<T, E>;

  T? get valueOrNull => switch (this) {
        Success(:final value) => value,
        Failure() => null,
      };

  E? get errorOrNull => switch (this) {
        Success() => null,
        Failure(:final error) => error,
      };

  /// 成功时执行 [onSuccess]，失败时执行 [onError]，返回统一类型 [R]。
  R fold<R>(R Function(T value) onSuccess, R Function(E error) onError) =>
      switch (this) {
        Success(:final value) => onSuccess(value),
        Failure(:final error) => onError(error),
      };
}

final class Success<T, E> extends Result<T, E> {
  const Success(this.value);
  final T value;
}

final class Failure<T, E> extends Result<T, E> {
  const Failure(this.error);
  final E error;
}
