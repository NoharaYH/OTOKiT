/// VPN 启动配置，由 GameModule.buildVpnConfig 产出，交给 VpnRepository.prepareAndStart。
/// 具体字段在 Phase 2 由 infrastructure 协议对象填充。
abstract class VpnStartConfig {
  const VpnStartConfig();
}
