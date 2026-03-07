import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'kernel/di/injection.dart';
import 'ui/pages/root_page.dart';
import 'application/shared/navigation_provider.dart';
import 'application/shared/game_provider.dart';
import 'application/transfer/transfer_provider.dart';
import 'application/shared/toast_provider.dart';
import 'application/mai/mai_music_provider.dart';
import 'application/chu/chu_music_provider.dart';

import 'ui/design_system/constants/strings.dart';
import 'ui/design_system/kit_shared/toast_queue_manager.dart';

void main() {
  configureDependencies();
  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => getIt<GameProvider>()),
        ChangeNotifierProvider(create: (_) => getIt<TransferProvider>()),
        ChangeNotifierProvider(create: (_) => getIt<NavigationProvider>()),
        ChangeNotifierProvider(create: (_) => getIt<ToastProvider>()),
        ChangeNotifierProvider(create: (_) => getIt<MaiMusicProvider>()),
        ChangeNotifierProvider(create: (_) => getIt<ChuMusicDataProvider>()),
      ],
      child: const MyApp(),
    ),
  );
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: UiStrings.appName,
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        fontFamily: 'JiangCheng',
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const ToastOverlay(child: RootPage()),
    );
  }
}
