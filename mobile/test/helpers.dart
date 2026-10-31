import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:jondeu_app/core/api.dart';

/// In-memory HTTP backend for tests: route handler gets (method, path, body, headers).
class FakeAdapter implements HttpClientAdapter {
  final List<String> log = [];
  final ResponseBody Function(RequestOptions o) handler;
  FakeAdapter(this.handler);
  @override
  Future<ResponseBody> fetch(RequestOptions o, Stream<Uint8List>? s, Future<void>? c) async {
    log.add('${o.method} ${o.path} ${o.headers['Authorization'] ?? ''}');
    return handler(o);
  }

  @override
  void close({bool force = false}) {}
}

ResponseBody jsonRes(Object body, [int status = 200]) => ResponseBody.fromString(jsonEncode(body), status, headers: {Headers.contentTypeHeader: [Headers.jsonContentType]});

ApiClient fakeClient(FakeAdapter a) => ApiClient(dio: Dio(BaseOptions(baseUrl: 'http://fake'))..httpClientAdapter = a, store: MemoryTokenStore());

Json tokens(String access, String refresh) => {'accessToken': access, 'refreshToken': refresh, 'expiresInSeconds': 1800, 'user': {'id': 'u1', 'fullName': 'Асель Нурланова', 'role': 'CUSTOMER', 'phone': '+77010000001'}};
