import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/api.dart';
import 'core/i18n.dart';
import 'core/session.dart';
import 'core/tokens.dart';
import 'ui.dart';

final categoriesProvider = FutureProvider<List<Json>>((ref) async => ((await ref.read(apiProvider).get('/api/v1/catalog/categories')) as List).cast<Json>());

/// 5-step renovation request wizard. Validates each step; persists to the backend as a draft, then submits.
class RequestWizard extends ConsumerStatefulWidget {
  const RequestWizard({super.key});
  @override
  ConsumerState<RequestWizard> createState() => _RequestWizardState();
}

class _RequestWizardState extends ConsumerState<RequestWizard> {
  int step = 0;
  bool busy = false;
  final keys = List.generate(4, (_) => GlobalKey<FormState>());
  final name = TextEditingController(), area = TextEditingController(), rooms = TextEditingController(text: '2'), address = TextEditingController(), desc = TextEditingController();
  final bMin = TextEditingController(text: '0'), bMax = TextEditingController();
  String type = 'APARTMENT';
  String? category;
  bool materials = true;
  DateTime? start;

  Json get body => {
        'name': name.text.trim(), 'propertyType': type, 'rooms': int.tryParse(rooms.text) ?? 0, 'areaM2': num.parse(area.text), 'address': address.text.trim(), 'description': desc.text.trim(),
        'category': category, 'budgetMin': num.tryParse(bMin.text) ?? 0, 'budgetMax': num.parse(bMax.text), 'materialsIncluded': materials, if (start != null) 'startDate': start!.toIso8601String().substring(0, 10),
      };

  Future<void> submit(bool send) async {
    setState(() => busy = true);
    final api = ref.read(apiProvider);
    final ok = await act(context, ref, () async {
      final p = await api.post('/api/v1/projects', body);
      if (send) await api.post('/api/v1/projects/${p['id']}/submit');
      ref.invalidate(projectsProvider);
      ref.read(selectedProjectProvider.notifier).set(p['id'] as String);
    }, ok: send ? 'requestSent' : 'done');
    if (!mounted) return;
    if (ok) {
      Navigator.pop(context);
    } else {
      setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final s = ref.watch(sProvider);
    final cats = ref.watch(categoriesProvider).value ?? const [];
    String? req(String? v) => (v ?? '').trim().length < 2 ? s.t('valRequired') : null;
    String? pos(String? v) => (num.tryParse(v ?? '') ?? 0) <= 0 ? s.t('valNumber') : null;
    final titles = [s.t('stepProperty'), s.t('stepNeeds'), s.t('stepBudget'), s.t('stepTimeline'), s.t('stepConfirm')];
    final forms = <Widget>[
      Form(key: keys[0], child: Column(children: [
        TextFormField(controller: name, decoration: InputDecoration(labelText: s.t('projectName')), validator: req),
        const SizedBox(height: 12),
        SegmentedButton<String>(segments: [ButtonSegment(value: 'APARTMENT', label: Text(s.t('apartment'))), ButtonSegment(value: 'HOUSE', label: Text(s.t('house')))], selected: {type}, onSelectionChanged: (v) => setState(() => type = v.first)),
        const SizedBox(height: 12),
        Row(children: [Expanded(child: TextFormField(controller: area, keyboardType: TextInputType.number, decoration: InputDecoration(labelText: s.t('area')), validator: pos)), const SizedBox(width: 12), Expanded(child: TextFormField(controller: rooms, keyboardType: TextInputType.number, decoration: InputDecoration(labelText: s.t('rooms'))))]),
        const SizedBox(height: 12),
        TextFormField(controller: address, decoration: InputDecoration(labelText: s.t('address')), validator: req),
      ])),
      Form(key: keys[1], child: Column(children: [
        DropdownButtonFormField<String>(initialValue: category, decoration: InputDecoration(labelText: s.t('category')), items: [for (final c in cats) DropdownMenuItem(value: c['code'] as String, child: Text(c[ref.watch(langProvider)] as String, overflow: TextOverflow.ellipsis))], onChanged: (v) => setState(() => category = v), validator: (v) => v == null ? s.t('valRequired') : null),
        const SizedBox(height: 12),
        TextFormField(controller: desc, maxLines: 5, decoration: InputDecoration(labelText: s.t('description')), validator: (v) => (v ?? '').trim().length < 10 ? s.t('valRequired') : null),
      ])),
      Form(key: keys[2], child: Column(children: [
        TextFormField(controller: bMin, keyboardType: TextInputType.number, decoration: InputDecoration(labelText: s.t('budgetMin'))),
        const SizedBox(height: 12),
        TextFormField(controller: bMax, keyboardType: TextInputType.number, decoration: InputDecoration(labelText: s.t('budgetMax')), validator: (v) { final m = num.tryParse(v ?? '') ?? 0; return m <= 0 ? s.t('valNumber') : m < (num.tryParse(bMin.text) ?? 0) ? s.t('valNumber') : null; }),
        SwitchListTile(contentPadding: EdgeInsets.zero, value: materials, onChanged: (v) => setState(() => materials = v), title: Text(s.t('materialsIncluded'))),
      ])),
      Form(key: keys[3], child: ListTile(contentPadding: EdgeInsets.zero, title: Text(s.t('startDate')), subtitle: Text(start == null ? '—' : s.date(start!.toIso8601String())), trailing: const Icon(Icons.calendar_month),
          onTap: () async { final d = await showDatePicker(context: context, firstDate: DateTime.now(), lastDate: DateTime.now().add(const Duration(days: 730)), initialDate: start ?? DateTime.now()); if (d != null) setState(() => start = d); })),
      Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        for (final (k, v) in [(s.t('projectName'), name.text), (s.t('address'), address.text), (s.t('area'), area.text), (s.t('category'), '${category ?? ''}'), (s.t('description'), desc.text), (s.t('budget'), '${bMin.text} – ${bMax.text} ₸')])
          Padding(padding: const EdgeInsets.only(bottom: 10), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(k, style: const TextStyle(color: T.inkSoft, fontSize: 12)), Text(v, style: const TextStyle(fontWeight: FontWeight.w600))])),
      ]),
    ];
    return Scaffold(
      appBar: AppBar(title: Text(s.t('newRequest'))),
      body: Column(children: [
        LinearProgressIndicator(value: (step + 1) / 5, minHeight: 4, color: T.blue, backgroundColor: T.line),
        Padding(padding: const EdgeInsets.fromLTRB(20, 14, 20, 0), child: Align(alignment: Alignment.centerLeft, child: Text('${step + 1}/5 · ${titles[step]}', style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16)))),
        Expanded(child: SingleChildScrollView(padding: const EdgeInsets.all(20), child: forms[step])),
        SafeArea(child: Padding(padding: const EdgeInsets.all(16), child: Row(children: [
          if (step > 0) Expanded(child: OutlinedButton(onPressed: busy ? null : () => setState(() => step--), child: Text(s.t('back')))),
          if (step > 0) const SizedBox(width: 12),
          Expanded(flex: 2, child: step < 4
              ? FilledButton(onPressed: () { if (keys[step].currentState!.validate()) setState(() => step++); }, child: Text(s.t('next')))
              : FilledButton(onPressed: busy ? null : () => submit(true), child: Text(s.t('submitRequest')))),
        ]))),
      ]),
    );
  }
}

/// ---------------- professionals: open requests + proposals ----------------
final openRequestsProvider = FutureProvider<List<Json>>((ref) async => ((await ref.read(apiProvider).get('/api/v1/requests/open', query: {'size': 30}))['content'] as List).cast<Json>());

class OpenRequestsScreen extends ConsumerWidget {
  const OpenRequestsScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(sProvider);
    return Scaffold(
      appBar: AppBar(title: Text(s.t('openRequests'))),
      body: RefreshIndicator(onRefresh: () async => ref.refresh(openRequestsProvider.future), child: Async<List<Json>>(ref.watch(openRequestsProvider), onRetry: () => ref.invalidate(openRequestsProvider), data: (list) => list.isEmpty
          ? ListView(children: [SizedBox(height: 400, child: Empty(s.t('noRequests')))])
          : ListView.separated(padding: const EdgeInsets.all(16), itemCount: list.length, separatorBuilder: (_, __) => const SizedBox(height: 10), itemBuilder: (_, i) {
              final r = list[i];
              return Panel(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Row(children: [Expanded(child: Text('${r['name']}', style: const TextStyle(fontWeight: FontWeight.w700))), StatusChip(r['status'] as String)]),
                Text('${r['areaM2']} m² · ${r['city']} · ${s.money(n(r['budgetMin']))} – ${s.money(n(r['budgetMax']))}', style: const TextStyle(color: T.inkSoft, fontSize: 12)),
                if (r['description'] != null) Padding(padding: const EdgeInsets.only(top: 6), child: Text('${r['description']}', maxLines: 3, overflow: TextOverflow.ellipsis)),
                const SizedBox(height: 8),
                r['myProposal'] != null ? StatusChip(r['myProposal'] as String) : FilledButton(style: FilledButton.styleFrom(minimumSize: const Size(160, 42)), onPressed: () => showModalBottomSheet(context: context, isScrollControlled: true, showDragHandle: true, builder: (_) => BidSheet(projectId: r['id'] as String)), child: Text(s.t('bid'))),
              ]));
            }))),
    );
  }
}

class BidSheet extends ConsumerStatefulWidget {
  final String projectId;
  const BidSheet({super.key, required this.projectId});
  @override
  ConsumerState<BidSheet> createState() => _BidSheetState();
}

class _BidSheetState extends ConsumerState<BidSheet> {
  final price = TextEditingController(), days = TextEditingController(), msg = TextEditingController();
  bool busy = false;
  @override
  Widget build(BuildContext context) {
    final s = ref.watch(sProvider);
    final ok = (num.tryParse(price.text) ?? 0) > 0 && (int.tryParse(days.text) ?? 0) > 0;
    return Padding(padding: EdgeInsets.fromLTRB(20, 0, 20, MediaQuery.of(context).viewInsets.bottom + 20), child: Column(mainAxisSize: MainAxisSize.min, children: [
      TextField(controller: price, keyboardType: TextInputType.number, onChanged: (_) => setState(() {}), decoration: InputDecoration(labelText: s.t('price'))),
      const SizedBox(height: 12),
      TextField(controller: days, keyboardType: TextInputType.number, onChanged: (_) => setState(() {}), decoration: InputDecoration(labelText: s.t('duration'))),
      const SizedBox(height: 12),
      TextField(controller: msg, maxLines: 3, decoration: InputDecoration(labelText: s.t('message'))),
      const SizedBox(height: 16),
      FilledButton(onPressed: !ok || busy ? null : () async {
        setState(() => busy = true);
        final nav = Navigator.of(context);
        final done = await act(context, ref, () => ref.read(apiProvider).post('/api/v1/projects/${widget.projectId}/proposals', {'price': num.parse(price.text), 'durationDays': int.parse(days.text), 'message': msg.text}), ok: 'done');
        if (done) { ref.invalidate(openRequestsProvider); nav.pop(); } else if (mounted) { setState(() => busy = false); }
      }, child: Text(s.t('send'))),
    ]));
  }
}

/// ---------------- customer: compare proposals ----------------
class ProposalsScreen extends ConsumerWidget {
  final Json project;
  const ProposalsScreen({super.key, required this.project});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(sProvider);
    final pid = project['id'] as String;
    return Scaffold(
      appBar: AppBar(title: Text(s.t('proposals'))),
      body: Async<dynamic>(ref.watch(projectResource('proposals', pid)), onRetry: () => ref.invalidate(projectResource('proposals', pid)), data: (list) {
        final ps = (list as List).cast<Json>();
        if (ps.isEmpty) return Empty(s.t('noProposals'));
        final best = ps.map((p) => n(p['price'])).reduce((a, b) => a < b ? a : b);
        return ListView(padding: const EdgeInsets.all(16), children: [for (final p in ps) Padding(padding: const EdgeInsets.only(bottom: 10), child: Panel(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [Expanded(child: Text('${p['masterName']}', style: const TextStyle(fontWeight: FontWeight.w700))), StatusChip(p['status'] as String)]),
          Text(s.money(n(p['price'])), style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800, color: n(p['price']) == best && ps.length > 1 ? T.success : T.ink)),
          Text('${p['durationDays']} ${s.t('days')}', style: const TextStyle(color: T.inkSoft)),
          if (p['message'] != null) Text('${p['message']}'),
          if (p['status'] == 'PENDING' && project['myRole'] == 'OWNER') Padding(padding: const EdgeInsets.only(top: 8), child: Row(children: [
            Expanded(child: FilledButton(onPressed: () async { if (await act(context, ref, () => ref.read(apiProvider).post('/api/v1/proposals/${p['id']}/accept'), ok: 'done')) { ref.invalidate(projectsProvider); if (context.mounted) Navigator.pop(context); } }, child: Text(s.t('accept')))),
            const SizedBox(width: 8),
            Expanded(child: OutlinedButton(onPressed: () => act(context, ref, () => ref.read(apiProvider).post('/api/v1/proposals/${p['id']}/reject'), ok: 'done'), child: Text(s.t('reject')))),
          ])),
        ])))]);
      }),
    );
  }
}
