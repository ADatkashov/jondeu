import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/api.dart';
import 'core/i18n.dart';
import 'core/session.dart';
import 'core/tokens.dart';
import 'screens_project.dart';
import 'screens_request.dart';
import 'ui.dart';

class RtDot extends ConsumerWidget {
  const RtDot({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final st = ref.watch(rtProvider);
    final s = ref.watch(sProvider);
    final c = st == RtState.online ? T.success : st == RtState.connecting ? T.warning : T.inkSoft;
    return Tooltip(message: s.t(st == RtState.online ? 'live' : st == RtState.connecting ? 'connecting' : 'offline'), child: Row(mainAxisSize: MainAxisSize.min, children: [
      Icon(Icons.circle, size: 9, color: c), const SizedBox(width: 4),
      Text(st == RtState.online ? s.t('live') : st == RtState.connecting ? s.t('connecting') : '', style: TextStyle(fontSize: 11, color: c, fontWeight: FontWeight.w600)),
    ]));
  }
}

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(sProvider);
    final projects = ref.watch(projectsProvider);
    final project = ref.watch(currentProjectProvider);
    final user = ref.watch(authProvider).user;
    return Scaffold(
      appBar: AppBar(title: Text('JONDEU'), actions: [const RtDot(), const SizedBox(width: 8), IconButton(tooltip: s.t('notifications'), icon: const Icon(Icons.notifications_none), onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const NotificationsScreen()))), const SizedBox(width: 4)]),
      body: RefreshIndicator(
        onRefresh: () async => ref.refresh(projectsProvider.future),
        child: Async<List<Json>>(projects, onRetry: () => ref.invalidate(projectsProvider), data: (list) => list.isEmpty
            ? ListView(children: [SizedBox(height: 400, child: Empty(s.t('noProjects'), hint: s.t('noProjectsHint'), action: isProRole(user?['role']) ? null : FilledButton(onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const RequestWizard())), child: Text(s.t('newRequest')))))])
            : ListView(padding: const EdgeInsets.all(16), children: [
                DropdownButtonFormField<String>(initialValue: project?['id'] as String?, decoration: InputDecoration(labelText: s.t('projects')), items: [for (final p in list) DropdownMenuItem(value: p['id'] as String, child: Text(p['name'] as String, overflow: TextOverflow.ellipsis))], onChanged: (v) => ref.read(selectedProjectProvider.notifier).set(v)),
                const SizedBox(height: 12),
                if (project != null) OverviewBody(project: project),
              ])),
      ),
    );
  }
}

class OverviewBody extends ConsumerWidget {
  final Json project;
  const OverviewBody({super.key, required this.project});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(sProvider);
    final pid = project['id'] as String;
    final ov = ref.watch(projectResource('overview', pid));
    final act_ = ref.watch(projectResource('activity', pid));
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Row(children: [Expanded(child: Text(project['name'] as String, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800))), StatusChip(project['status'] as String)]),
      if (project['demo'] == true) Padding(padding: const EdgeInsets.only(top: 4), child: Text(s.t('demo'), style: const TextStyle(color: T.orange, fontSize: 11, fontWeight: FontWeight.w700))),
      const SizedBox(height: 12),
      Async<dynamic>(ov, onRetry: () => ref.invalidate(projectResource('overview', pid)), data: (o) {
        final f = o['finance'] as Json;
        final pr = o['progress'] as Json;
        final sch = o['schedule'] as Json;
        final approved = n(f['approvedBudget']);
        return Column(children: [
          Panel(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(s.t('budget'), style: const TextStyle(color: T.inkSoft, fontSize: 12)),
            Text(s.money(approved), style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w800)),
            const SizedBox(height: 10),
            Bar(n(f['utilizationPercent']).toDouble(), color: T.success),
            const SizedBox(height: 10),
            Row(children: [_kv(s.t('paid'), s.money(n(f['paid']))), _kv(s.t('remaining'), s.money(n(f['remaining'])), color: T.success)]),
          ])),
          const SizedBox(height: 12),
          Row(children: [
            Expanded(child: Panel(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(s.t('progress'), style: const TextStyle(color: T.inkSoft, fontSize: 12)), Text('${n(pr['percent']).toStringAsFixed(0)}%', style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800)), const SizedBox(height: 6), Bar(n(pr['percent']).toDouble())]))),
            const SizedBox(width: 12),
            Expanded(child: Panel(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(s.t('deadline'), style: const TextStyle(color: T.inkSoft, fontSize: 12)), Text(s.date(sch['endDate'] as String?), style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800)), Text(sch['daysLeft'] == null ? '' : '${sch['daysLeft']} ${s.t('leftDays')}', style: const TextStyle(color: T.inkSoft, fontSize: 12))]))),
          ]),
          const SizedBox(height: 12),
          Panel(title: s.t('quality'), trailing: StatusChip((o['quality'] as Json)['status'] as String), child: const SizedBox.shrink()),
          const SizedBox(height: 12),
          ProjectActions(project: project),
        ]);
      }),
      const SizedBox(height: 12),
      Panel(title: s.t('activity'), child: Async<dynamic>(act_, onRetry: () => ref.invalidate(projectResource('activity', pid)), data: (list) => Column(children: [
        for (final e in (list as List).take(8))
          ListTile(dense: true, contentPadding: EdgeInsets.zero, leading: const Icon(Icons.history, size: 18, color: T.blue), title: Text('${e['message'] ?? e['type']}', style: const TextStyle(fontSize: 14)), subtitle: Text(s.dateTime(e['createdAt'] as String), style: const TextStyle(fontSize: 11))),
      ]))),
    ]);
  }

  Widget _kv(String k, String v, {Color? color}) => Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(k, style: const TextStyle(color: T.inkSoft, fontSize: 12)), Text(v, style: TextStyle(fontWeight: FontWeight.w700, color: color))]));
}

class ProjectActions extends ConsumerWidget {
  final Json project;
  const ProjectActions({super.key, required this.project});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(sProvider);
    final id = project['id'] as String;
    void open(Widget w) => Navigator.push(context, MaterialPageRoute(builder: (_) => w));
    final items = <(IconData, String, Widget)>[
      (Icons.checklist, s.t('stages'), StagesScreen(project: project)),
      (Icons.calculate_outlined, s.t('estimate'), EstimatesScreen(project: project)),
      (Icons.payments_outlined, s.t('payments'), PaymentsScreen(project: project)),
      (Icons.inventory_2_outlined, s.t('materials'), MaterialsScreen(projectId: id)),
      (Icons.photo_camera_outlined, s.t('photos'), PhotosScreen(projectId: id)),
    ];
    return Column(children: [
      GridView.count(crossAxisCount: 3, shrinkWrap: true, physics: const NeverScrollableScrollPhysics(), mainAxisSpacing: 10, crossAxisSpacing: 10, childAspectRatio: 1.15, children: [
        for (final it in items) InkWell(borderRadius: BorderRadius.circular(16), onTap: () => open(it.$3), child: Ink(decoration: BoxDecoration(color: T.surface, borderRadius: BorderRadius.circular(16), border: Border.all(color: T.line)), child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [Icon(it.$1, color: T.navy, size: 28), const SizedBox(height: 6), Text(it.$2, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600))]))),
      ]),
      if (project['myRole'] == 'OWNER' && project['status'] == 'IN_PROGRESS') Padding(padding: const EdgeInsets.only(top: 12), child: FilledButton(onPressed: () async { if (await act(context, ref, () => ref.read(apiProvider).post('/api/v1/projects/$id/complete'), ok: 'done')) ref.invalidate(projectsProvider); }, child: Text(s.t('complete')))),
    ]);
  }
}

class NotificationsScreen extends ConsumerWidget {
  const NotificationsScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(sProvider);
    return Scaffold(
      appBar: AppBar(title: Text(s.t('notifications')), actions: [TextButton(onPressed: () async { await ref.read(apiProvider).post('/api/v1/notifications/read-all'); ref.invalidate(notificationsProvider); }, child: const Icon(Icons.done_all))]),
      body: Async<Json>(ref.watch(notificationsProvider), onRetry: () => ref.invalidate(notificationsProvider), data: (d) {
        final list = (d['content'] as List).cast<Json>();
        if (list.isEmpty) return Empty(s.t('notifications'));
        return ListView(children: [for (final n_ in list) ListTile(leading: Icon(Icons.circle, size: 10, color: n_['read'] == true ? T.line : T.orange), title: Text('${n_['title']}', style: TextStyle(fontWeight: n_['read'] == true ? FontWeight.w400 : FontWeight.w700)), subtitle: Text('${n_['body'] ?? ''} · ${s.dateTime(n_['createdAt'] as String)}'))]);
      }),
    );
  }
}
