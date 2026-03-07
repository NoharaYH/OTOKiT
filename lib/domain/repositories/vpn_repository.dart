import '../entities/vpn_start_config.dart';
import '../entities/vpn_status.dart';

/// VPN 仓储端口：启动/停止、状态与日志流。
/// 实现在 infrastructure/native（VpnChannelGateway），application 仅依赖此接口。
abstract class VpnRepository {
  Future<void> prepareAndStart(VpnStartConfig config);
  Future<bool> stop();
  Future<void> notifyDivingFishTaskDone();
  Stream<VpnStatus> get statusStream;
  Stream<String> get logStream;
}
