import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:jondeu_app/core/api.dart';
import 'package:jondeu_app/core/i18n.dart';
import 'package:jondeu_app/core/tokens.dart';
import 'package:jondeu_app/screens_auth.dart';
import 'package:jondeu_app/screens_project.dart';
import 'package:jondeu_app/ui.dart';

import 'helpers.dart';

Widget host(Widget child, FakeAdapter a) => ProviderScope(
      overrides: [apiProvider.overrideWithValue(fakeClient(a))],
      child: MaterialApp(theme: T.theme(), home: child),
    );

void main() {
  testWidgets('auth screen validates the phone and password before calling the backend', (t) async {
    final a = FakeAdapter((o) => jsonRes(tokens('a', 'r')));
    await t.pumpWidget(host(const AuthScreen(), a));
    await t.enterText(find.byType(TextFormField).first, '123');
    await t.tap(find.byType(FilledButton));
    await t.pump();
    expect(find.text('Введите номер Казахстана'), findsOneWidget);
    expect(a.log, isEmpty);
  });

  testWidgets('auth screen sends the normalised phone', (t) async {
    final a = FakeAdapter((o) => jsonRes(tokens('a', 'r')));
    await t.pumpWidget(host(const AuthScreen(), a));
    await t.enterText(find.byType(TextFormField).at(0), '8 701 123 45 67');
    await t.enterText(find.byType(TextFormField).at(1), 'secret123');
    await t.tap(find.byType(FilledButton));
    await t.pump(const Duration(milliseconds: 50));
    expect(a.log.single, startsWith('POST /api/v1/auth/login'));
  });

  testWidgets('language switch re-renders the UI in Kazakh', (t) async {
    await t.pumpWidget(host(const AuthScreen(), FakeAdapter((o) => jsonRes({}))));
    expect(find.text('Войти'), findsOneWidget);
    await t.tap(find.text('KK'));
    await t.pump();
    expect(find.text('Кіру'), findsOneWidget);
  });

  testWidgets('payment sheet blocks amounts above the available balance', (t) async {
    await t.pumpWidget(host(const Scaffold(body: PaySheet(projectId: 'p', max: 1000)), FakeAdapter((o) => jsonRes({}))));
    final pay = find.widgetWithText(FilledButton, 'Оплатить');
    expect(tester(pay, t).onPressed, isNull); // empty amount
    await t.enterText(find.byType(TextField), '1500');
    await t.pump();
    expect(find.text('Сумма больше остатка'), findsOneWidget);
    expect(tester(pay, t).onPressed, isNull);
    await t.enterText(find.byType(TextField), '800');
    await t.pump();
    expect(tester(pay, t).onPressed, isNotNull);
  });

  testWidgets('payment sheet sends one idempotency key and the exact amount', (t) async {
    String? key;
    Object? body;
    final a = FakeAdapter((o) { key = o.headers['Idempotency-Key'] as String?; body = o.data; return jsonRes({'id': 'x'}); });
    await t.pumpWidget(host(const Scaffold(body: PaySheet(projectId: 'p', max: 5000)), a));
    await t.enterText(find.byType(TextField), '2500');
    await t.pump();
    await t.tap(find.widgetWithText(FilledButton, 'Оплатить'));
    await t.pump(const Duration(milliseconds: 100));
    expect(key, isNotNull);
    expect((body as Map)['amount'], 2500);
    expect(a.log.where((l) => l.startsWith('POST /api/v1/projects/p/payments')).length, 1);
  });

  testWidgets('StatusChip shows localized status', (t) async {
    await t.pumpWidget(host(const Scaffold(body: StatusChip('IN_PROGRESS')), FakeAdapter((o) => jsonRes({}))));
    expect(find.text('В работе'), findsOneWidget);
  });

  testWidgets('Async shows an error with retry', (t) async {
    var retried = 0;
    await t.pumpWidget(host(Scaffold(body: Async<int>(AsyncValue<int>.error(ApiException(0, 'NETWORK', 'x'), StackTrace.empty), data: (_) => const SizedBox(), onRetry: () => retried++)), FakeAdapter((o) => jsonRes({}))));
    expect(find.text('Нет соединения с сервером'), findsOneWidget);
    await t.tap(find.text('Повторить'));
    expect(retried, 1);
  });
}

FilledButton tester(Finder f, WidgetTester t) => t.widget<FilledButton>(f);
