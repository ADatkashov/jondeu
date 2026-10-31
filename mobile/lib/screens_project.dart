import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

import 'core/api.dart';
import 'core/i18n.dart';
import 'core/session.dart';
import 'core/tokens.dart';
import 'ui.dart';

bool _isPro(Json p) => const ['CONTRACTOR', 'PROJECT_MANAGER', 'ADMIN'].contains(p['myRole']);

/// ---------------- stages ----------------
class StagesScreen extends ConsumerWidget {
  final Json project;
  const StagesScreen({super.key, required this.project});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(sProvider);
    final pid = project['id'] as String;
    final role = project['myRole'];
    final work = const ['CONTRACTOR', 'PROJECT_MANAGER', 'MASTER', 'ADMIN'].contains(role);
    final accept = const ['OWNER', 'INSPECTOR', 'ADMIN'].contains(role);
    Future<void> patch(String id, Json body) async { await act(context, ref, () => ref.read(apiProvider).patch('/api/v1/stages/$id', body)); }
    return Scaffold(
      appBar: AppBar(title: Text(s.t('stages'))),
      body: Async<dynamic>(ref.watch(projectResource('stages', pid)), onRetry: () => ref.invalidate(projectResource('stages', pid)), data: (list) {
        final stages = (list as List).cast<Json>();
        if (stages.isEmpty) return Empty(s.t('noStages'));
        return ListView.separated(padding: const EdgeInsets.all(16), itemCount: stages.length, separatorBuilder: (_, __) => const SizedBox(height: 10), itemBuilder: (_, i) {
          final st = stages[i];
          final status = st['status'] as String;
          return Panel(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [CircleAvatar(radius: 14, backgroundColor: T.navy, child: Text('${st['position']}', style: const TextStyle(color: Colors.white, fontSize: 12))), const SizedBox(width: 10), Expanded(child: Text(st['title'] as String, style: const TextStyle(fontWeight: FontWeight.w700))), StatusChip(status)]),
            const SizedBox(height: 4),
            Text('${s.date(st['startDate'] as String?)} → ${s.date((st['actualEndDate'] ?? st['endDate']) as String?)} · ${s.money(n(st['budget']))}', style: const TextStyle(color: T.inkSoft, fontSize: 12)),
            const SizedBox(height: 8),
            Row(children: [Expanded(child: Bar(n(st['progress']).toDouble(), color: status == 'COMPLETED' ? T.success : T.blue)), const SizedBox(width: 8), Text('${st['progress']}%', style: const TextStyle(fontWeight: FontWeight.w700))]),
            if (work && !['COMPLETED', 'UNDER_REVIEW'].contains(status)) _ProgressSlider(value: n(st['progress']).toDouble(), onDone: (v) => patch(st['id'] as String, {'progress': v.round()})),
            Wrap(spacing: 8, children: [
              if (work && status == 'NOT_STARTED') OutlinedButton(onPressed: () => patch(st['id'] as String, {'status': 'IN_PROGRESS'}), child: Text(s.t('stages'))),
              if (work && ['IN_PROGRESS', 'DELAYED'].contains(status)) OutlinedButton(onPressed: () => patch(st['id'] as String, {'status': 'UNDER_REVIEW'}), child: Text(s.t('requestReview'))),
              if (accept && status == 'UNDER_REVIEW') ...[FilledButton(style: FilledButton.styleFrom(minimumSize: const Size(120, 42), backgroundColor: T.success), onPressed: () => patch(st['id'] as String, {'status': 'COMPLETED'}), child: Text(s.t('acceptStage'))), OutlinedButton(onPressed: () => patch(st['id'] as String, {'status': 'IN_PROGRESS'}), child: Text(s.t('returnStage')))],
            ]),
          ]));
        });
      }),
    );
  }
}

class _ProgressSlider extends StatefulWidget {
  final double value;
  final ValueChanged<double> onDone;
  const _ProgressSlider({required this.value, required this.onDone});
  @override
  State<_ProgressSlider> createState() => _ProgressSliderState();
}

class _ProgressSliderState extends State<_ProgressSlider> {
  late double v = widget.value;
  @override
  void didUpdateWidget(_ProgressSlider o) { super.didUpdateWidget(o); v = widget.value; }
  @override
  Widget build(BuildContext context) => Slider(value: v.clamp(0, 100), min: 0, max: 100, divisions: 20, label: '${v.round()}%', onChanged: (x) => setState(() => v = x), onChangeEnd: widget.onDone);
}

/// ---------------- estimates ----------------
class EstimatesScreen extends ConsumerWidget {
  final Json project;
  const EstimatesScreen({super.key, required this.project});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(sProvider);
    final pid = project['id'] as String;
    final isOwner = project['myRole'] == 'OWNER';
    Future<void> call(String id, String action, {Json? body, String? confirmKey}) async {
      if (confirmKey != null) {
        final ok = await showDialog<bool>(context: context, builder: (c) => AlertDialog(content: Text(s.t(confirmKey)), actions: [TextButton(onPressed: () => Navigator.pop(c, false), child: Text(s.t('cancel'))), FilledButton(style: FilledButton.styleFrom(minimumSize: const Size(100, 44)), onPressed: () => Navigator.pop(c, true), child: Text(s.t('approve')))]));
        if (ok != true || !context.mounted) return;
      }
      if (await act(context, ref, () => ref.read(apiProvider).post('/api/v1/estimates/$id/$action', body ?? {}), ok: 'done')) ref.invalidate(projectsProvider);
    }
    return Scaffold(
      appBar: AppBar(title: Text(s.t('estimate'))),
      body: Async<dynamic>(ref.watch(projectResource('estimates', pid)), onRetry: () => ref.invalidate(projectResource('estimates', pid)), data: (list) {
        final ests = (list as List).cast<Json>();
        if (ests.isEmpty) return Empty(s.t('noEstimates'));
        return ListView(padding: const EdgeInsets.all(16), children: [
          for (final e in ests) Padding(padding: const EdgeInsets.only(bottom: 12), child: Panel(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [Expanded(child: Text('${e['number']} · v${e['version']}', style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16))), StatusChip(e['status'] as String)]),
            if (e['status'] == 'APPROVED') Padding(padding: const EdgeInsets.only(top: 2), child: Text(s.t('locked'), style: const TextStyle(color: T.inkSoft, fontSize: 12))),
            const Divider(height: 20),
            for (final i in (e['items'] as List)) Padding(padding: const EdgeInsets.symmetric(vertical: 3), child: Row(children: [Expanded(child: Text('${i['title']}', style: const TextStyle(fontSize: 14))), Text(s.money(n(i['total'])), style: const TextStyle(fontWeight: FontWeight.w600))])),
            const Divider(height: 20),
            _line(s.t('subtotal'), s.money(n(e['subtotal']))), _line(s.t('additional'), s.money(n(e['additionalCosts']))), _line(s.t('discount'), '−${s.money(n(e['discount']))}'), _line('${s.t('tax')} ${e['taxRate']}%', s.money(n(e['taxAmount']))),
            _line(s.t('total'), s.money(n(e['total'])), bold: true),
            if (e['status'] == 'APPROVED') ...[_line(s.t('paid'), s.money(n(e['paid']))), _line(s.t('remaining'), s.money(n(e['remaining'])), bold: true)],
            if (e['reviewComment'] != null) Padding(padding: const EdgeInsets.only(top: 8), child: Text('${e['reviewComment']}', style: const TextStyle(color: T.warning))),
            if (isOwner && e['status'] == 'SUBMITTED') Padding(padding: const EdgeInsets.only(top: 12), child: Column(children: [
              FilledButton(style: FilledButton.styleFrom(backgroundColor: T.success), onPressed: () => call(e['id'] as String, 'approve', confirmKey: 'confirmApprove'), child: Text(s.t('approve'))),
              const SizedBox(height: 8),
              OutlinedButton(onPressed: () => call(e['id'] as String, 'request-changes', body: {'comment': ''}), child: Text(s.t('requestChanges'))),
              TextButton(onPressed: () => call(e['id'] as String, 'reject', body: {'comment': ''}), child: Text(s.t('reject'), style: const TextStyle(color: T.danger))),
            ])),
            if (_isPro(project) && e['status'] == 'DRAFT') Padding(padding: const EdgeInsets.only(top: 12), child: FilledButton(onPressed: () => call(e['id'] as String, 'submit'), child: Text(s.t('send')))),
          ]))),
        ]);
      }),
    );
  }

  Widget _line(String k, String v, {bool bold = false}) => Padding(padding: const EdgeInsets.symmetric(vertical: 2), child: Row(children: [Expanded(child: Text(k, style: TextStyle(color: bold ? T.ink : T.inkSoft, fontWeight: bold ? FontWeight.w800 : FontWeight.w400))), Text(v, style: TextStyle(fontWeight: bold ? FontWeight.w800 : FontWeight.w600, fontSize: bold ? 17 : 14))]));
}

/// ---------------- payments ----------------
class PaymentsScreen extends ConsumerWidget {
  final Json project;
  const PaymentsScreen({super.key, required this.project});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(sProvider);
    final pid = project['id'] as String;
    final owner = project['myRole'] == 'OWNER';
    final ov = ref.watch(projectResource('overview', pid));
    final pays = ref.watch(projectResource('payments', pid));
    final fin = ov.value?['finance'] as Json?;
    final available = fin == null ? 0.0 : max(0.0, n(fin['approvedBudget']).toDouble() - n(fin['paid']).toDouble() - n(fin['reserved']).toDouble());
    return Scaffold(
      appBar: AppBar(title: Text(s.t('payments'))),
      floatingActionButton: owner && available > 0 ? FloatingActionButton.extended(onPressed: () => showModalBottomSheet(context: context, isScrollControlled: true, showDragHandle: true, builder: (_) => PaySheet(projectId: pid, max: available)), label: Text(s.t('payNow')), icon: const Icon(Icons.credit_card)) : null,
      body: ListView(padding: const EdgeInsets.all(16), children: [
        if (fin != null) Panel(child: Row(children: [for (final (k, v) in [(s.t('budget'), n(fin['approvedBudget'])), (s.t('paid'), n(fin['paid'])), (s.t('remaining'), n(fin['remaining']))]) Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(k, style: const TextStyle(color: T.inkSoft, fontSize: 12)), Text(s.money(v), style: const TextStyle(fontWeight: FontWeight.w800))]))])),
        const SizedBox(height: 12),
        Text(s.t('history'), style: const TextStyle(fontWeight: FontWeight.w700)),
        const SizedBox(height: 8),
        Async<dynamic>(pays, onRetry: () => ref.invalidate(projectResource('payments', pid)), data: (list) {
          final ps = (list as List).cast<Json>();
          if (ps.isEmpty) return Padding(padding: const EdgeInsets.all(24), child: Empty(s.t('noPayments')));
          return Column(children: [for (final p in ps) Padding(padding: const EdgeInsets.only(bottom: 8), child: _PaymentTile(p: p, project: project))]);
        }),
      ]),
    );
  }
}

class _PaymentTile extends ConsumerWidget {
  final Json p, project;
  const _PaymentTile({required this.p, required this.project});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(sProvider);
    final api = ref.read(apiProvider);
    final status = p['status'] as String;
    final owner = project['myRole'] == 'OWNER';
    return Panel(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Row(children: [Expanded(child: Text(s.money(n(p['amount'])), style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 17))), StatusChip(status)]),
      Text('${p['reference']} · ${s.t(p['type'] as String)} · ${s.t(p['method'] as String)}', style: const TextStyle(color: T.inkSoft, fontSize: 12)),
      if (p['sandbox'] == true) const Padding(padding: EdgeInsets.only(top: 4), child: Text('SANDBOX', style: TextStyle(color: T.warning, fontSize: 11, fontWeight: FontWeight.w800))),
      if (p['note'] != null) Text('${p['note']}', style: const TextStyle(fontSize: 13)),
      Wrap(spacing: 8, children: [
        if (owner && status == 'REQUESTED') FilledButton(style: FilledButton.styleFrom(minimumSize: const Size(110, 40)), onPressed: () => act(context, ref, () => api.post('/api/v1/payments/${p['id']}/pay', {'method': 'BANK_TRANSFER'}), ok: 'paymentCreated'), child: Text(s.t('payNow'))),
        if (_isPro(project) && status == 'PENDING' && p['provider'] == 'MANUAL') FilledButton(style: FilledButton.styleFrom(minimumSize: const Size(140, 40), backgroundColor: T.success), onPressed: () => act(context, ref, () => api.post('/api/v1/payments/${p['id']}/confirm'), ok: 'done'), child: Text(s.t('confirmReceipt'))),
        if (owner && p['sandbox'] == true && status == 'PENDING') OutlinedButton(onPressed: () => act(context, ref, () => api.post('/api/v1/payments/${p['id']}/sandbox-resolve', {'success': true}), ok: 'done'), child: Text(s.t('sandboxConfirm'))),
      ]),
    ]));
  }
}

class PaySheet extends ConsumerStatefulWidget {
  final String projectId;
  final double max;
  const PaySheet({super.key, required this.projectId, required this.max});
  @override
  ConsumerState<PaySheet> createState() => _PaySheetState();
}

class _PaySheetState extends ConsumerState<PaySheet> {
  // One idempotency key per sheet: double taps / retries can never create two payments.
  final _key = 'm-${DateTime.now().microsecondsSinceEpoch}-${Random().nextInt(1 << 32)}';
  final _amount = TextEditingController();
  String _method = 'BANK_TRANSFER', _type = 'STAGE';
  bool _busy = false;

  @override
  Widget build(BuildContext context) {
    final s = ref.watch(sProvider);
    final v = num.tryParse(_amount.text) ?? 0;
    final valid = v > 0 && v <= widget.max;
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 0, 20, MediaQuery.of(context).viewInsets.bottom + 20),
      child: SingleChildScrollView(child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Container(padding: const EdgeInsets.all(16), decoration: BoxDecoration(color: T.navy, borderRadius: BorderRadius.circular(16)), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(s.t('toPay'), style: const TextStyle(color: Colors.white70, fontSize: 12)), Text(v > 0 ? s.money(v) : '—', style: const TextStyle(color: Colors.white, fontSize: 30, fontWeight: FontWeight.w800)), Text('${s.t('available')}: ${s.money(widget.max)}', style: const TextStyle(color: Colors.white70, fontSize: 12))])),
        const SizedBox(height: 12),
        DropdownButtonFormField<String>(initialValue: _type, decoration: InputDecoration(labelText: s.t('category')), items: [for (final t in ['ADVANCE', 'STAGE', 'MATERIAL', 'FINAL', 'ADDITIONAL']) DropdownMenuItem(value: t, child: Text(s.t(t)))], onChanged: (x) => setState(() => _type = x!)),
        const SizedBox(height: 12),
        TextField(controller: _amount, keyboardType: TextInputType.number, onChanged: (_) => setState(() {}), decoration: InputDecoration(labelText: s.t('amount'), errorText: _amount.text.isNotEmpty && v > widget.max ? s.t('exceeds') : _amount.text.isNotEmpty && v <= 0 ? s.t('valNumber') : null)),
        const SizedBox(height: 8),
        Text(s.t('payMethod'), style: const TextStyle(color: T.inkSoft, fontSize: 12)),
        RadioGroup<String>(groupValue: _method, onChanged: (x) => setState(() => _method = x!), child: Column(children: [for (final m in ['BANK_TRANSFER', 'CARD', 'KASPI', 'CASH']) RadioListTile<String>(dense: true, value: m, title: Text(s.t(m)))])),
        Text(_method == 'CARD' || _method == 'KASPI' ? s.t('sandboxNote') : s.t('manualNote'), style: const TextStyle(color: T.inkSoft, fontSize: 12)),
        const SizedBox(height: 12),
        FilledButton(onPressed: !valid || _busy ? null : () async {
          setState(() => _busy = true);
          final nav = Navigator.of(context);
          final ok = await act(context, ref, () => ref.read(apiProvider).post('/api/v1/projects/${widget.projectId}/payments', {'type': _type, 'method': _method, 'amount': v}, {'Idempotency-Key': _key}), ok: 'paymentCreated');
          if (ok) { nav.pop(); } else if (mounted) { setState(() => _busy = false); }
        }, child: Text(s.t('payNow'))),
      ])),
    );
  }
}

/// ---------------- materials ----------------
class MaterialsScreen extends ConsumerWidget {
  final String projectId;
  const MaterialsScreen({super.key, required this.projectId});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(sProvider);
    return Scaffold(
      appBar: AppBar(title: Text(s.t('materials'))),
      body: Async<dynamic>(ref.watch(projectResource('materials', projectId)), onRetry: () => ref.invalidate(projectResource('materials', projectId)), data: (d) {
        final items = (d['items'] as List).cast<Json>();
        if (items.isEmpty) return Empty(s.t('noMaterials'));
        final sum = d['summary'] as Json;
        return ListView(padding: const EdgeInsets.all(16), children: [
          Panel(child: Row(children: [Expanded(child: Text('${s.t('planned')}\n${s.money(n(sum['plannedCost']))}', style: const TextStyle(fontWeight: FontWeight.w700))), Expanded(child: Text('${s.t('actual')}\n${s.money(n(sum['actualCost']))}', style: const TextStyle(fontWeight: FontWeight.w700)))])),
          const SizedBox(height: 10),
          for (final m in items) Padding(padding: const EdgeInsets.only(bottom: 8), child: Panel(child: Row(children: [Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text('${m['name']}', style: const TextStyle(fontWeight: FontWeight.w700)), Text('${m['supplier'] ?? ''} · ${n(m['receivedQty'])}/${n(m['plannedQty'])} ${m['unit']} · ${s.date(m['deliveryDate'] as String?)}', style: const TextStyle(color: T.inkSoft, fontSize: 12))])), StatusChip(m['status'] as String)]))),
        ]);
      }),
    );
  }
}

/// ---------------- photos ----------------
class PhotosScreen extends ConsumerWidget {
  final String projectId;
  const PhotosScreen({super.key, required this.projectId});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(sProvider);
    return Scaffold(
      appBar: AppBar(title: Text(s.t('photos'))),
      floatingActionButton: FloatingActionButton.extended(icon: const Icon(Icons.add_a_photo_outlined), label: Text(s.t('addPhoto')), onPressed: () async {
        final x = await ImagePicker().pickImage(source: ImageSource.gallery, imageQuality: 85, maxWidth: 2400);
        if (x == null || !context.mounted) return;
        await act(context, ref, () => ref.read(apiProvider).upload('/api/v1/projects/$projectId/media', x.path, x.name, {'category': 'DURING'}), ok: 'done');
      }),
      body: Async<dynamic>(ref.watch(projectResource('media', projectId)), onRetry: () => ref.invalidate(projectResource('media', projectId)), data: (list) {
        final imgs = (list as List).cast<Json>().where((m) => '${m['contentType']}'.startsWith('image/')).toList();
        if (imgs.isEmpty) return Empty(s.t('noPhotos'));
        return GridView.builder(padding: const EdgeInsets.all(12), gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(crossAxisCount: 2, mainAxisSpacing: 10, crossAxisSpacing: 10, childAspectRatio: .95), itemCount: imgs.length, itemBuilder: (_, i) {
          final m = imgs[i];
          return ClipRRect(borderRadius: BorderRadius.circular(14), child: Stack(fit: StackFit.expand, children: [
            Image.network(mediaUrl(m['url'] as String), fit: BoxFit.cover, errorBuilder: (_, __, ___) => const ColoredBox(color: T.line, child: Icon(Icons.broken_image))),
            Positioned(left: 0, right: 0, bottom: 0, child: Container(color: Colors.black54, padding: const EdgeInsets.all(6), child: Text('${m['caption'] ?? m['filename']}', maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(color: Colors.white, fontSize: 12)))),
          ]));
        });
      }),
    );
  }
}
