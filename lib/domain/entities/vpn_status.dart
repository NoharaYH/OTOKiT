/// VPN 状态，由 VpnRepository.statusStream 产出。
class VpnStatus {
  const VpnStatus({required this.isRunning, this.statusText});

  final bool isRunning;
  final String? statusText;

  bool get isDone => statusText == 'done' || !isRunning;
}
