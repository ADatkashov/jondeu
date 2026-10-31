import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/i18n.dart';
import 'core/session.dart';
import 'core/tokens.dart';
import 'screens_auth.dart';
import 'screens_home.dart';
import 'core/api.dart';
import 'ui.dart';
import 'screens_request.dart';
import 'screens_social.dart';

class JondeuApp extends ConsumerWidget {
  const JondeuApp({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final auth = ref.watch(authProvider);
    return MaterialApp(
      title: 'JONDEU',
      debugShowCheckedModeBanner: false,
      theme: T.theme(),
      home: !auth.ready ? const Scaffold(body: Center(child: CircularProgressIndicator())) : auth.user == null ? const AuthScreen() : const MainShell(),
    );
  }
}

class MainShell extends ConsumerStatefulWidget {
  const MainShell({super.key});
  @override
  ConsumerState<MainShell> createState() => _MainShellState();
}

class _MainShellState extends ConsumerState<MainShell> {
  int index = 0;

  @override
  Widget build(BuildContext context) {
    final s = ref.watch(sProvider);
    final pro = isProRole(ref.watch(authProvider).user?['role'] as String?);
    ref.watch(rtProvider); // keep the realtime connection alive for the whole signed-in session
    final unread = (ref.watch(conversationsProvider).value ?? const []).fold<num>(0, (a, c) => a + (c['unread'] as num));
    final project = ref.watch(currentProjectProvider);
    final pages = <Widget>[
      const HomeScreen(),
      pro ? const OpenRequestsScreen() : const MastersScreen(),
      _ProjectsTab(onWizard: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const RequestWizard()))),
      const ChatListScreen(),
      const ProfileScreen(),
    ];
    return Scaffold(
      body: IndexedStack(index: index, children: pages),
      floatingActionButton: null,
      bottomNavigationBar: NavigationBar(selectedIndex: index, onDestinationSelected: (i) => setState(() => index = i), destinations: [
        NavigationDestination(icon: const Icon(Icons.home_outlined), selectedIcon: const Icon(Icons.home), label: s.t('home')),
        NavigationDestination(icon: Icon(pro ? Icons.inbox_outlined : Icons.engineering_outlined), label: s.t(pro ? 'requests' : 'masters')),
        NavigationDestination(icon: const Icon(Icons.folder_outlined), selectedIcon: const Icon(Icons.folder), label: s.t('projects')),
        NavigationDestination(icon: Badge(isLabelVisible: unread > 0, label: Text('$unread'), child: const Icon(Icons.chat_bubble_outline)), label: s.t('chat')),
        NavigationDestination(icon: const Icon(Icons.person_outline), selectedIcon: const Icon(Icons.person), label: s.t('profile')),
      ]),
      // project context is shown to keep screens consistent; unused var silences lints when no project
      key: ValueKey(project?['id']),
    );
  }
}

class _ProjectsTab extends ConsumerWidget {
  final VoidCallback onWizard;
  const _ProjectsTab({required this.onWizard});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(sProvider);
    final user = ref.watch(authProvider).user;
    return Scaffold(
      appBar: AppBar(title: Text(s.t('projects'))),
      floatingActionButton: isProRole(user?['role'] as String?) ? null : FloatingActionButton.extended(onPressed: onWizard, icon: const Icon(Icons.add), label: Text(s.t('newRequest'))),
      body: Async<List<Json>>(ref.watch(projectsProvider), onRetry: () => ref.invalidate(projectsProvider), data: (list) => list.isEmpty ? Empty(s.t('noProjects'), hint: s.t('noProjectsHint')) : ListView.separated(padding: const EdgeInsets.all(16), itemCount: list.length, separatorBuilder: (_, __) => const SizedBox(height: 10), itemBuilder: (_, i) {
        final p = list[i];
        final open = ['SUBMITTED', 'UNDER_REVIEW', 'PROPOSALS_RECEIVED'].contains(p['status']) && p['myRole'] == 'OWNER';
        return InkWell(borderRadius: BorderRadius.circular(16), onTap: () => open ? Navigator.push(context, MaterialPageRoute(builder: (_) => ProposalsScreen(project: p))) : ref.read(selectedProjectProvider.notifier).set(p['id'] as String), child: Panel(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [Expanded(child: Text('${p['name']}', style: const TextStyle(fontWeight: FontWeight.w700))), StatusChip(p['status'] as String)]),
          Text('${p['address'] ?? p['city']} · ${p['areaM2']} m²', style: const TextStyle(color: T.inkSoft, fontSize: 12)),
          if (open) Padding(padding: const EdgeInsets.only(top: 6), child: Text(s.t('proposals'), style: const TextStyle(color: T.orange, fontWeight: FontWeight.w700))),
        ])));
      })),
    );
  }
}
