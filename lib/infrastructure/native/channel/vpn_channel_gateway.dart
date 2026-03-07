import 'dart:async';

import 'package:flutter/services.dart';
import 'package:injectable/injectable.dart';

import '../../../domain/entities/vpn_start_config.dart';
import '../../../domain/entities/vpn_status.dart';
import '../../../domain/repositories/vpn_repository.dart';
import '../../../shared/constants/infra_constants.dart';
import '../models/vpn_start_payload.dart';

@lazySingleton
class VpnChannelGateway implements VpnRepository {
  VpnChannelGateway() {
    _channel.setMethodCallHandler(_handleCall);
  }

  static final MethodChannel _channel =
      MethodChannel(InfraConstants.vpnChannelName);

  final _statusController = StreamController<VpnStatus>.broadcast();
  final _logController = StreamController<String>.broadcast();

  VpnStartConfig? _pendingConfig;

  @override
  Stream<VpnStatus> get statusStream => _statusController.stream;

  @override
  Stream<String> get logStream => _logController.stream;

  Future<dynamic> _handleCall(MethodCall call) async {
    switch (call.method) {
      case 'onStatusChanged':
        final args = call.arguments as Map<dynamic, dynamic>?;
        if (args != null) {
          final isRunning = args['isRunning'] as bool? ?? false;
          final status = args['status'] as String?;
          _statusController.add(VpnStatus(isRunning: isRunning, statusText: status));
        }
        break;
      case 'onLogReceived':
        _logController.add(call.arguments as String);
        break;
      case 'onVpnPrepared':
        if (call.arguments == true && _pendingConfig != null) {
          await _doStart(_pendingConfig!);
          _pendingConfig = null;
        }
        break;
    }
  }

  @override
  Future<void> prepareAndStart(VpnStartConfig config) async {
    final ok = await _channel.invokeMethod<bool>('prepareVpn');
    if (ok == true) {
      await _doStart(config);
    } else {
      _pendingConfig = config;
    }
  }

  Future<void> _doStart(VpnStartConfig config) async {
    final payload = VpnStartPayload.fromConfig(config);
    await _channel.invokeMethod('startVpn', payload.toMap());
  }

  @override
  Future<bool> stop() async {
    await _channel.invokeMethod('stopVpn');
    _pendingConfig = null;
    return true;
  }

  @override
  Future<void> notifyDivingFishTaskDone() async {
    await _channel.invokeMethod('notifyDivingFishTaskDone');
  }

  void dispose() {
    _statusController.close();
    _logController.close();
  }
}
