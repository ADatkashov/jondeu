import 'package:flutter_test/flutter_test.dart';
import 'package:jondeu_app/core/api.dart';
import 'package:jondeu_app/core/i18n.dart';
import 'package:jondeu_app/screens_auth.dart';

import 'helpers.dart';

void main() {
  group('i18n', () {
    test('every key has ru, kk and en text', () => expect(S.testKeysComplete, isTrue));
    test('formats tenge and dates per locale', () {
      expect(const S('ru').money(12455000).replaceAll(RegExp(r'\s'), ' '), '12 455 000 ₸');
      expect(const S('en').t('payNow'), 'Pay');
      expect(const S('kk').t('payNow'), 'Төлеу');
      expect(const S('ru').t('missing.key'), 'missing.key');
      expect(const S('ru').err('EXCEEDS_OUTSTANDING', 'x'), contains('остатк'));
    });
  });

  test('Kazakhstan phone normalisation mirrors the backend', () {
    expect(normalizePhone('8 (701) 123-45-67'), '+77011234567');
    expect(normalizePhone('+7 701 123 45 67'), '+77011234567');
    expect(normalizePhone('12345'), isNull);
    expect(normalizePhone('+1 202 555 0100'), isNull);
  });

  group('ApiClient', () {
    test('rotates the refresh token once on 401 and retries with the new access token', () async {
      final a = FakeAdapter((o) {
        if (o.path == '/api/v1/auth/refresh') return jsonRes(tokens('new', 'r2'));
        return o.headers['Authorization'] == 'Bearer new' ? jsonRes({'ok': true}) : jsonRes({'code': 'X'}, 401);
      });
      final c = fakeClient(a);
      await c.setTokens(tokens('old', 'r1'));
      expect(await c.get('/api/v1/projects'), {'ok': true});
      expect(a.log.where((l) => l.contains('/auth/refresh')).length, 1);
      expect((await c.store.read())!['refreshToken'], 'r2');
    });

    test('concurrent 401s share a single refresh', () async {
      final a = FakeAdapter((o) {
        if (o.path == '/api/v1/auth/refresh') return jsonRes(tokens('new', 'r2'));
        return o.headers['Authorization'] == 'Bearer new' ? jsonRes({}) : jsonRes({}, 401);
      });
      final c = fakeClient(a);
      await c.setTokens(tokens('old', 'r1'));
      await Future.wait([c.get('/a'), c.get('/b'), c.get('/c')]);
      expect(a.log.where((l) => l.contains('/auth/refresh')).length, 1);
    });

    test('failed refresh signs the user out', () async {
      final a = FakeAdapter((o) => jsonRes({'code': 'INVALID_REFRESH', 'message': 'no'}, 401));
      final c = fakeClient(a);
      await c.setTokens(tokens('old', 'r1'));
      final out = expectLater(c.onForcedLogout, emits(anything));
      await expectLater(c.get('/api/v1/projects'), throwsA(isA<ApiException>()));
      await out;
      expect(c.signedIn, isFalse);
    });

    test('maps backend error bodies to ApiException with code', () async {
      final c = fakeClient(FakeAdapter((o) => jsonRes({'code': 'EXCEEDS_OUTSTANDING', 'message': 'too much'}, 422)));
      await expectLater(c.post('/x'), throwsA(isA<ApiException>().having((e) => e.code, 'code', 'EXCEEDS_OUTSTANDING').having((e) => e.status, 'status', 422)));
    });
  });
}
