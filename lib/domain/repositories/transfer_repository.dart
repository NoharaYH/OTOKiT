import '../../shared/errors/network_exception.dart';
import '../../shared/result/result.dart';

/// 传分上传仓储端口：上传舞萌/中二记录到对应平台。
/// 实现在 infrastructure/network，domain usecase 仅依赖此接口。
abstract class TransferRepository {
  Future<Result<void, NetworkException>> uploadMaimaiRecords(
    String token,
    List<Map<String, dynamic>> records,
  );
}
