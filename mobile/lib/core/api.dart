import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

typedef Json = Map<String, dynamic>;

/// Backend base URL. Android emulator reaches the host at 10.0.2.2.
const apiBase = String.fromEnvironment('API_URL', defaultValue: 'http://10.0.2.2:8080');
String get wsBase => apiBase.replaceFirst(RegExp('^http'), 'ws');

class ApiException implements Exception {
  final int status;
  final String code;
  final String message;
  ApiException(this.status, this.code, this.message);
  @override
  String toString() => message;
}

abstract class TokenStore {
  Future<Json?> read();
  Future<void> write(Json? tokens);
}

class SecureTokenStore implements TokenStore {
  static const _k = 'jondeu.tokens';
  final _s = const FlutterSecureStorage();
  @override
  Future<Json?> read() async {
    final v = await _s.read(key: _k);
    return v == null ? null : jsonDecode(v) as Json;
  }

  @override
  Future<void> write(Json? t) async => t == null ? _s.delete(key: _k) : _s.write(key: _k, value: jsonEncode(t));
}

class MemoryTokenStore implements TokenStore {
  Json? _v;
  @override
  Future<Json?> read() async => _v;
  @override
  Future<void> write(Json? t) async => _v = t;
}

/// Dio wrapper: bearer auth, transparent single-flight refresh-token rotation, uniform errors.
class ApiClient {
  final Dio dio;
  final TokenStore store;
  Json? _tokens;
  Future<bool>? _refreshing;
  final _logout = StreamController<void>.broadcast();
  Stream<void> get onForcedLogout => _logout.stream;

  ApiClient({Dio? dio, required this.store}) : dio = dio ?? Dio(BaseOptions(baseUrl: apiBase, connectTimeout: const Duration(seconds: 10), receiveTimeout: const Duration(seconds: 30))) {
    this.dio.interceptors.add(InterceptorsWrapper(
      onRequest: (o, h) {
        final t = _tokens?['accessToken'];
        if (t != null) o.headers['Authorization'] = 'Bearer $t';
        h.next(o);
      },
      onError: (e, h) async {
        final req = e.requestOptions;
        if (e.response?.statusCode == 401 && req.extra['retried'] != true && !req.path.contains('/auth/') && _tokens != null) {
          final sent = (req.headers['Authorization'] as String?)?.substring(7);
          if (sent != _tokens!['accessToken'] || await _refresh()) {
            req.extra['retried'] = true;
            req.headers['Authorization'] = 'Bearer ${_tokens!['accessToken']}';
            try {
              return h.resolve(await this.dio.fetch(req));
            } on DioException catch (e2) {
              return h.next(e2);
            }
          }
        }
        h.next(e);
      },
    ));
  }

  bool get signedIn => _tokens != null;
  Json? get user => _tokens?['user'] as Json?;
  String? get accessToken => _tokens?['accessToken'] as String?;

  Future<Json?> restore() async {
    _tokens = await store.read();
    return user;
  }

  Future<void> setTokens(Json? t) async {
    _tokens = t;
    await store.write(t);
  }

  Future<bool> _refresh() => _refreshing ??= () async {
        try {
          final r = await (Dio(BaseOptions(baseUrl: dio.options.baseUrl))..httpClientAdapter = dio.httpClientAdapter).post('/api/v1/auth/refresh', data: {'refreshToken': _tokens!['refreshToken']});
          await setTokens(r.data as Json);
          return true;
        } catch (_) {
          await setTokens(null);
          _logout.add(null);
          return false;
        } finally {
          _refreshing = null;
        }
      }();

  /// Fresh access token for WebSocket handshakes.
  Future<String?> freshToken() async {
    final t = accessToken;
    if (t == null) return null;
    try {
      final exp = (jsonDecode(utf8.decode(base64Url.decode(base64Url.normalize(t.split('.')[1])))) as Json)['exp'] as int;
      if (exp * 1000 - DateTime.now().millisecondsSinceEpoch < 60000) await _refresh();
    } catch (_) {
      await _refresh();
    }
    return accessToken;
  }

  Future<T> _wrap<T>(Future<Response<dynamic>> Function() f) async {
    try {
      return (await f()).data as T;
    } on DioException catch (e) {
      final d = e.response?.data;
      if (d is Map) throw ApiException(e.response!.statusCode ?? 0, '${d['code'] ?? 'ERROR'}', '${d['message'] ?? 'Error'}');
      throw ApiException(e.response?.statusCode ?? 0, e.response == null ? 'NETWORK' : 'ERROR', e.message ?? 'Network error');
    }
  }

  Future<dynamic> get(String p, {Json? query}) => _wrap<dynamic>(() => dio.get(p, queryParameters: query));
  Future<dynamic> post(String p, [Object? body, Json? headers]) => _wrap<dynamic>(() => dio.post(p, data: body ?? {}, options: Options(headers: headers)));
  Future<dynamic> patch(String p, Object body) => _wrap<dynamic>(() => dio.patch(p, data: body));
  Future<dynamic> put(String p, Object body) => _wrap<dynamic>(() => dio.put(p, data: body));
  Future<dynamic> upload(String p, String path, String filename, Map<String, String> fields) =>
      _wrap<dynamic>(() async => dio.post(p, data: FormData.fromMap({...fields, 'file': await MultipartFile.fromFile(path, filename: filename)})));
}

final tokenStoreProvider = Provider<TokenStore>((_) => SecureTokenStore());
final apiProvider = Provider<ApiClient>((ref) => ApiClient(store: ref.watch(tokenStoreProvider)));

/// Absolute URL for signed media links returned by the backend.
String mediaUrl(String path) => path.startsWith('http') ? path : '$apiBase$path';
