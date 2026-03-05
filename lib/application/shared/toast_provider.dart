import 'package:flutter/material.dart';
import 'package:injectable/injectable.dart';

enum ToastType { verifying, confirmed, error, warning }

class ToastItem {
  final String id;
  final String message;
  final ToastType type;
  final DateTime createdAt;

  ToastItem({
    required this.id,
    required this.message,
    required this.type,
    required this.createdAt,
  });
}

@lazySingleton
class ToastProvider extends ChangeNotifier {
  final List<ToastItem> _toasts = [];

  List<ToastItem> get toasts => List.unmodifiable(_toasts);

  void show(String message, ToastType type) {
    final id = DateTime.now().microsecondsSinceEpoch.toString();
    final toast = ToastItem(
      id: id,
      message: message,
      type: type,
      createdAt: DateTime.now(),
    );

    _toasts.add(toast);
    notifyListeners();

    Future.delayed(const Duration(seconds: 4), () {
      remove(id);
    });
  }

  void remove(String id) {
    _toasts.removeWhere((t) => t.id == id);
    notifyListeners();
  }
}
