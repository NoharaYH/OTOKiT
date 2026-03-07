// GENERATED CODE - DO NOT MODIFY BY HAND
// dart format width=80

// **************************************************************************
// InjectableConfigGenerator
// **************************************************************************

// ignore_for_file: type=lint
// coverage:ignore-file

// ignore_for_file: no_leading_underscores_for_library_prefixes
import 'package:dio/dio.dart' as _i361;
import 'package:flutter_secure_storage/flutter_secure_storage.dart' as _i558;
import 'package:get_it/get_it.dart' as _i174;
import 'package:injectable/injectable.dart' as _i526;

import '../../application/chu/chu_music_provider.dart' as _i331;
import '../../application/mai/mai_music_provider.dart' as _i1008;
import '../../application/shared/game_provider.dart' as _i822;
import '../../application/shared/navigation_provider.dart' as _i155;
import '../../application/shared/toast_provider.dart' as _i533;
import '../../application/transfer/transfer_provider.dart' as _i1034;
import '../../logic/mai_music_data/data_sync/mai_oss_sync_handler.dart'
    as _i790;
import '../services/api_service.dart' as _i137;
import '../services/storage_service.dart' as _i306;
import '../storage/sql/app_database.dart' as _i903;
import '../storage/sql/daos/mai_music_dao.dart' as _i80;
import 'register_module.dart' as _i291;

extension GetItInjectableX on _i174.GetIt {
  // initializes the registration of main-scope dependencies inside of GetIt
  _i174.GetIt init({
    String? environment,
    _i526.EnvironmentFilter? environmentFilter,
  }) {
    final gh = _i526.GetItHelper(this, environment, environmentFilter);
    final registerModule = _$RegisterModule();
    gh.factory<_i331.ChuMusicDataProvider>(() => _i331.ChuMusicDataProvider());
    gh.factory<_i155.NavigationProvider>(() => _i155.NavigationProvider());
    gh.factory<_i790.MaiOssSyncHandler>(() => _i790.MaiOssSyncHandler());
    gh.lazySingleton<_i533.ToastProvider>(() => _i533.ToastProvider());
    gh.lazySingleton<_i361.Dio>(() => registerModule.dio);
    gh.lazySingleton<_i558.FlutterSecureStorage>(
      () => registerModule.secureStorage,
    );
    gh.lazySingleton<_i903.AppDatabase>(() => _i903.AppDatabase());
    gh.lazySingleton<_i80.MaiMusicDao>(
      () => _i80.MaiMusicDao(gh<_i903.AppDatabase>()),
    );
    gh.lazySingleton<_i306.StorageService>(
      () => _i306.StorageService(gh<_i558.FlutterSecureStorage>()),
    );
    gh.lazySingleton<_i137.ApiService>(() => _i137.ApiService(gh<_i361.Dio>()));
    gh.factory<_i1008.MaiMusicProvider>(
      () => _i1008.MaiMusicProvider(
        gh<_i80.MaiMusicDao>(),
        gh<_i790.MaiOssSyncHandler>(),
        gh<_i533.ToastProvider>(),
      ),
    );
    gh.factory<_i822.GameProvider>(
      () => _i822.GameProvider(gh<_i306.StorageService>()),
    );
    gh.factory<_i1034.TransferProvider>(
      () => _i1034.TransferProvider(
        gh<_i137.ApiService>(),
        gh<_i306.StorageService>(),
      ),
    );
    return this;
  }
}

class _$RegisterModule extends _i291.RegisterModule {}
