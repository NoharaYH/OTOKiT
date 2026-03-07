import 'dart:convert';

import '../data_formats/mai_song_row.dart';

/// 将 OSS 拉取的普通曲 JSON 数组解析为 [MaiMusicRow]。
/// 单条结构：{ "basic_info": {...}, "charts": [...] }
List<MaiMusicRow> parseNormalMusicList(List<dynamic> jsonList) {
  return jsonList.map((item) {
    final basic = item['basic_info'] as Map<String, dynamic>;
    final version = basic['version'] as Map<String, dynamic>;
    final chartsRaw = item['charts'] as List<dynamic>? ?? [];
    final chartsJson = _encodeChartsFromOss(chartsRaw);
    return MaiMusicRow(
      id: basic['id'] as int,
      title: basic['title'] as String,
      artist: basic['artist'] as String,
      bpm: basic['bpm'] as int,
      type: basic['type'] as String,
      genre: basic['genre'] as String,
      versionText: version['text'] as String,
      versionId: version['id'] as int,
      chartsJson: chartsJson,
    );
  }).toList();
}

/// OSS charts 项: { difficulty, label, level, constant, designer, notes: { total, tap, ... } }
/// 转为 MaiChartRow 序列化格式的 JSON 字符串（与 [encodeCharts] 一致）。
String _encodeChartsFromOss(List<dynamic> charts) {
  final list = charts.map((c) {
    final notes = c['notes'] as Map<String, dynamic>;
    return {
      'difficulty': c['difficulty'],
      'label': c['label'],
      'level': c['level'],
      'constant': c['constant'],
      'designer': c['designer'],
      'tap': notes['tap'],
      'hold': notes['hold'],
      'slide': notes['slide'],
      'touch': notes['touch'],
      'break': notes['break'],
      'total': notes['total'],
    };
  }).toList();
  return jsonEncode(list);
}

/// 将 OSS 拉取的宴谱 JSON 数组解析为 [MaiUtageRow]。
/// 单条结构：{ "basic_info": {...}, "utage_info": {...}, "utage_charts": [...] }
List<MaiUtageRow> parseUtageList(List<dynamic> jsonList) {
  return jsonList.map((item) {
    final basic = item['basic_info'] as Map<String, dynamic>;
    final version = basic['version'] as Map<String, dynamic>;
    final utageInfo = item['utage_info'] as Map<String, dynamic>? ?? {};
    final utageCharts = item['utage_charts'] as List<dynamic>? ?? [];
    return MaiUtageRow(
      id: basic['id'] as int,
      title: basic['title'] as String,
      artist: basic['artist'] as String,
      bpm: basic['bpm'] as int,
      type: basic['type'] as String,
      versionText: version['text'] as String,
      versionId: version['id'] as int,
      utageInfoJson: jsonEncode(utageInfo),
      utageChartsJson: jsonEncode(utageCharts),
    );
  }).toList();
}
