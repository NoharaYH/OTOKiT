import 'package:get_it/get_it.dart';
import 'package:injectable/injectable.dart';

import '../../domain/repositories/vpn_repository.dart';
import '../../infrastructure/native/channel/vpn_channel_gateway.dart';
import '../config/prod_env.dart';
import '../../shared/env/app_env.dart';
import 'injection.config.dart';

final getIt = GetIt.instance;

@InjectableInit(
  initializerName: 'init', // default
  preferRelativeImports: true, // default
  asExtension: true, // default
)
void configureDependencies() {
  getIt.init();
  getIt.registerLazySingleton<VpnRepository>(() => getIt<VpnChannelGateway>());
  getIt.registerLazySingleton<AppEnv>(() => getIt<ProdEnv>());
}
