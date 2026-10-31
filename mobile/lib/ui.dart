import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/api.dart';
import 'core/i18n.dart';
import 'core/tokens.dart';

class StatusChip extends ConsumerWidget {
  final String status;
  const StatusChip(this.status, {super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final c = T.statusColor(status);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
      decoration: BoxDecoration(color: c.withValues(alpha: .12), borderRadius: BorderRadius.circular(20)),
      child: Text(ref.watch(sProvider).t(status), style: TextStyle(color: c, fontSize: 12, fontWeight: FontWeight.w700)),
    );
  }
}

class Panel extends StatelessWidget {
  final String? title;
  final Widget child;
  final Widget? trailing;
  final EdgeInsets padding;
  const Panel({super.key, this.title, required this.child, this.trailing, this.padding = const EdgeInsets.all(16)});
  @override
  Widget build(BuildContext context) => Card(
        child: Padding(
          padding: padding,
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            if (title != null) Padding(padding: const EdgeInsets.only(bottom: 10), child: Row(children: [Expanded(child: Text(title!, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15))), if (trailing != null) trailing!])),
            child,
          ]),
        ),
      );
}

class Bar extends StatelessWidget {
  final double value; // 0..100
  final Color color;
  const Bar(this.value, {super.key, this.color = T.blue});
  @override
  Widget build(BuildContext context) => Semantics(
        value: '${value.round()}%',
        child: ClipRRect(borderRadius: BorderRadius.circular(8), child: LinearProgressIndicator(value: (value.clamp(0, 100)) / 100, minHeight: 8, backgroundColor: T.canvas, color: color)),
      );
}

class Empty extends StatelessWidget {
  final String title;
  final String? hint;
  final Widget? action;
  const Empty(this.title, {super.key, this.hint, this.action});
  @override
  Widget build(BuildContext context) => Center(
        child: Padding(padding: const EdgeInsets.all(32), child: Column(mainAxisSize: MainAxisSize.min, children: [
          const Icon(Icons.inbox_outlined, size: 40, color: T.inkSoft),
          const SizedBox(height: 8),
          Text(title, style: const TextStyle(fontWeight: FontWeight.w700), textAlign: TextAlign.center),
          if (hint != null) Padding(padding: const EdgeInsets.only(top: 4), child: Text(hint!, style: const TextStyle(color: T.inkSoft), textAlign: TextAlign.center)),
          if (action != null) Padding(padding: const EdgeInsets.only(top: 12), child: action),
        ])),
      );
}

String errorText(Object e, S s) => e is ApiException ? s.err(e.code, e.message) : s.t('error');

/// Loading / error(+retry) / data wrapper used by every remote screen.
class Async<D> extends ConsumerWidget {
  final AsyncValue<D> value;
  final Widget Function(D) data;
  final VoidCallback onRetry;
  const Async(this.value, {super.key, required this.data, required this.onRetry});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(sProvider);
    return value.when(
      skipLoadingOnReload: true,
      data: data,
      loading: () => const Center(child: Padding(padding: EdgeInsets.all(32), child: CircularProgressIndicator())),
      error: (e, _) => Center(child: Padding(padding: const EdgeInsets.all(24), child: Column(mainAxisSize: MainAxisSize.min, children: [
        const Icon(Icons.cloud_off, color: T.danger, size: 36),
        const SizedBox(height: 8),
        Text(errorText(e, s), textAlign: TextAlign.center),
        const SizedBox(height: 8),
        OutlinedButton(onPressed: onRetry, child: Text(s.t('retry'))),
      ]))),
    );
  }
}

void toast(BuildContext c, String m, {bool error = false}) =>
    ScaffoldMessenger.of(c).showSnackBar(SnackBar(content: Text(m), backgroundColor: error ? T.danger : T.navy, behavior: SnackBarBehavior.floating));

/// Runs a backend action with uniform success / error feedback.
Future<bool> act(BuildContext c, WidgetRef ref, Future<dynamic> Function() f, {String? ok}) async {
  final s = ref.read(sProvider);
  try {
    await f();
    if (ok != null && c.mounted) toast(c, s.t(ok));
    return true;
  } catch (e) {
    if (c.mounted) toast(c, errorText(e, s), error: true);
    return false;
  }
}

num n(dynamic v) => v is num ? v : num.tryParse('$v') ?? 0;
