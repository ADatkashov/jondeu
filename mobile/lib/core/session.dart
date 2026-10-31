import 'dart:async';
import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

import 'api.dart';

class AuthState {
  final Json? user;
  final bool ready;
  const AuthState({this.user, this.ready = false});
}

class AuthNotifier extends Notifier<AuthState> {
  late final ApiClient _api = ref.read(apiProvider);

  @override
  AuthState build() {
    final sub = _api.onForcedLogout.listen((_) => state = const AuthState(ready: true));
    ref.onDispose(sub.cancel);
    Future.microtask(() async => state = AuthState(user: await _api.restore(), ready: true));
    return const AuthState();
  }

  Future<void> login(String phone, String password) async => _accept(await _api.post('/api/v1/auth/login', {'phone': phone, 'password': password}));
  Future<void> register(Json body) async => _accept(await _api.post('/api/v1/auth/register', body));

  Future<void> _accept(dynamic t) async {
    await _api.setTokens(t as Json);
    state = AuthState(user: t['user'] as Json, ready: true);
  }

  Future<void> logout() async {
    try {
      final tokens = await _api.store.read();
      if (tokens != null) await _api.post('/api/v1/auth/logout', {'refreshToken': tokens['refreshToken']});
    } catch (_) {}
    await _api.setTokens(null);
    state = const AuthState(ready: true);
  }
}

final authProvider = NotifierProvider<AuthNotifier, AuthState>(AuthNotifier.new);
bool isProRole(String? r) => const ['MASTER', 'CONTRACTOR', 'COMPANY_ADMIN', 'PROJECT_MANAGER'].contains(r);

/// Selected project id (persisted only for the session).
class SelectedProject extends Notifier<String?> {
  @override
  String? build() => null;
  void set(String? id) => state = id;
}

final selectedProjectProvider = NotifierProvider<SelectedProject, String?>(SelectedProject.new);

final projectsProvider = FutureProvider<List<Json>>((ref) async {
  if (ref.watch(authProvider).user == null) return [];
  final r = await ref.read(apiProvider).get('/api/v1/projects', query: {'size': 100});
  return (r['content'] as List).cast<Json>();
});

final currentProjectProvider = Provider<Json?>((ref) {
  final list = ref.watch(projectsProvider).value ?? const [];
  if (list.isEmpty) return null;
  final id = ref.watch(selectedProjectProvider);
  return list.firstWhere((p) => p['id'] == id, orElse: () => list.firstWhere((p) => p['status'] == 'IN_PROGRESS', orElse: () => list.first));
});

enum RtState { offline, connecting, online }

class RtNotifier extends Notifier<RtState> {
  WebSocketChannel? _ch;
  StreamSubscription? _sub;
  Timer? _retry, _ping;
  int _attempt = 0;
  bool _closed = false;
  final _subscribed = <String>{};
  final events = StreamController<Json>.broadcast();

  @override
  RtState build() {
    ref.onDispose(_dispose);
    ref.listen(authProvider.select((a) => a.user?['id']), (_, id) => id == null ? _disconnect() : _connect(), fireImmediately: true);
    ref.listen(projectsProvider, (_, p) => _subscribeAll());
    return RtState.offline;
  }

  void _dispose() {
    _closed = true;
    _disconnect();
  }

  void _disconnect() {
    _retry?.cancel();
    _ping?.cancel();
    _sub?.cancel();
    _ch?.sink.close();
    _ch = null;
    _subscribed.clear();
    if (!_closed) state = RtState.offline;
  }

  Future<void> _connect() async {
    _disconnect();
    final api = ref.read(apiProvider);
    if (!api.signedIn) return;
    state = RtState.connecting;
    final token = await api.freshToken();
    if (token == null || _closed) return;
    try {
      final ch = WebSocketChannel.connect(Uri.parse('$wsBase/ws?token=$token'));
      _ch = ch;
      await ch.ready;
      _attempt = 0;
      state = RtState.online;
      ref.invalidate(projectsProvider); // catch up after (re)connect
      _subscribeAll();
      _ping = Timer.periodic(const Duration(seconds: 25), (_) => _send({'action': 'ping'}));
      _sub = ch.stream.listen(_onMessage, onDone: _scheduleRetry, onError: (_) => _scheduleRetry());
    } catch (_) {
      _scheduleRetry();
    }
  }

  void _scheduleRetry() {
    if (_closed || !ref.read(apiProvider).signedIn) return;
    state = RtState.offline;
    _retry?.cancel();
    final secs = (1 << (_attempt++ > 4 ? 4 : _attempt)).clamp(1, 15);
    _retry = Timer(Duration(seconds: secs), _connect);
  }

  void _send(Json m) {
    try {
      _ch?.sink.add(jsonEncode(m));
    } catch (_) {}
  }

  void _subscribeAll() {
    if (state != RtState.online) return;
    for (final p in ref.read(projectsProvider).value ?? const <Json>[]) {
      if (_subscribed.add(p['id'] as String)) _send({'action': 'subscribe', 'projectId': p['id']});
    }
  }

  static const _keys = {
    'project': ['project'], 'estimate': ['estimates'], 'stage': ['stages'], 'payment': ['payments'], 'material': ['materials'], 'inspection': ['inspections'], 'media': ['media'],
    'proposal': ['proposals'], 'member': ['members'], 'calendar': ['calendar'],
  };

  /// Every server event invalidates the affected providers; the backend stays the single source of truth.
  void _onMessage(dynamic raw) {
    final m = jsonDecode(raw as String) as Json;
    switch (m['kind']) {
      case 'project.event':
        final pid = m['projectId'] as String;
        for (final k in ['overview', 'activity', ...(_keys[m['entityType']] ?? const <String>[])]) {
          ref.invalidate(projectResource(k, pid));
        }
        if (m['entityType'] == 'project' || m['entityType'] == 'proposal') ref.invalidate(projectsProvider);
      case 'chat.message' || 'chat.read':
        ref.invalidate(conversationsProvider);
        ref.invalidate(messagesProvider(m['conversationId'] as String));
      case 'notification':
        ref.invalidate(notificationsProvider);
    }
    events.add(m);
  }
}

String Function(String) _p(String suffix) => (id) => '/api/v1/projects/$id/$suffix';
final Map<String, String Function(String)> _paths = {
  'overview': _p('overview'), 'activity': _p('activity?limit=15'), 'stages': _p('stages'), 'estimates': _p('estimates'), 'payments': _p('payments'), 'materials': _p('materials'),
  'inspections': _p('inspections'), 'media': _p('media'), 'proposals': _p('proposals'), 'members': _p('members'), 'project': _p('overview'),
};

/// Provider for a project-scoped resource using the same path map as realtime invalidation.
FutureProvider<dynamic> projectResource(String name, String pid) {
  final path = _paths[name]!(pid);
  return _cache.putIfAbsent('$name|$pid', () => FutureProvider<dynamic>((ref) => ref.read(apiProvider).get(path)));
}

final _cache = <String, FutureProvider<dynamic>>{};

final rtProvider = NotifierProvider<RtNotifier, RtState>(RtNotifier.new);
final conversationsProvider = FutureProvider<List<Json>>((ref) async => ((await ref.read(apiProvider).get('/api/v1/conversations')) as List).cast<Json>());
final messagesProvider = FutureProvider.family<List<Json>, String>((ref, id) async =>
    ((await ref.read(apiProvider).get('/api/v1/conversations/$id/messages', query: {'limit': 100})) as List).cast<Json>().reversed.toList());
final notificationsProvider = FutureProvider<Json>((ref) async => (await ref.read(apiProvider).get('/api/v1/notifications', query: {'size': 30})) as Json);
