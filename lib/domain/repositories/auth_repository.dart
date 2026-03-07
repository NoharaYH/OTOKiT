import '../entities/token_bundle.dart';
import '../value_objects/game_type.dart';
import '../../shared/errors/auth_exception.dart';
import '../../shared/result/result.dart';

/// 认证仓储端口：Token 验证、OAuth 兑换、刷新、持久化。
/// 实现在 infrastructure，domain/application 仅依赖此接口。
abstract class AuthRepository {
  Future<Result<TokenBundle, AuthException>> validateDivingFishToken(String token);
  Future<Result<TokenBundle, AuthException>> validateLxnsToken(
    String token,
    GameType game,
  );
  Future<Result<TokenBundle, AuthException>> exchangeLxnsCode(
    String code,
    String verifier,
  );
  Future<Result<TokenBundle, AuthException>> refreshLxnsToken(String refreshToken);
  Future<void> saveTokenBundle(TokenBundle bundle);
  Future<TokenBundle> loadTokenBundle();
}
