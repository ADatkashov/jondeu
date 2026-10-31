import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/api.dart';
import 'core/i18n.dart';
import 'core/session.dart';
import 'core/tokens.dart';
import 'screens_request.dart';
import 'ui.dart';

/// ---------------- masters directory ----------------
class MastersScreen extends ConsumerStatefulWidget {
  const MastersScreen({super.key});
  @override
  ConsumerState<MastersScreen> createState() => _MastersScreenState();
}

class _MastersScreenState extends ConsumerState<MastersScreen> {
  String q = '', category = '', sort = 'recommended';
  bool verified = false;

  @override
  Widget build(BuildContext context) {
    final s = ref.watch(sProvider);
    final lang = ref.watch(langProvider);
    final cats = ref.watch(categoriesProvider).value ?? const [];
    final query = {'q': q, 'category': category, 'sort': sort, if (verified) 'verified': 'true', 'size': 30}..removeWhere((k, v) => v == '');
    return Scaffold(
      appBar: AppBar(title: Text(s.t('masters'))),
      body: Column(children: [
        Padding(padding: const EdgeInsets.fromLTRB(16, 0, 16, 8), child: TextField(onChanged: (v) => setState(() => q = v), decoration: InputDecoration(hintText: s.t('search'), prefixIcon: const Icon(Icons.search)))),
        SizedBox(height: 44, child: ListView(scrollDirection: Axis.horizontal, padding: const EdgeInsets.symmetric(horizontal: 12), children: [
          Padding(padding: const EdgeInsets.symmetric(horizontal: 4), child: FilterChip(label: Text(s.t('onlyVerified')), selected: verified, onSelected: (v) => setState(() => verified = v))),
          Padding(padding: const EdgeInsets.symmetric(horizontal: 4), child: PopupMenuButton<String>(onSelected: (v) => setState(() => sort = v), child: Chip(avatar: const Icon(Icons.sort, size: 18), label: Text(s.t('sort'))), itemBuilder: (_) => [for (final (k, l) in [('recommended', 'sortRec'), ('rating', 'sortRating'), ('price', 'sortPrice'), ('experience', 'sortExp')]) CheckedPopupMenuItem(value: k, checked: sort == k, child: Text(s.t(l)))])),
          Padding(padding: const EdgeInsets.symmetric(horizontal: 4), child: ChoiceChip(label: Text(s.t('allCats')), selected: category == '', onSelected: (_) => setState(() => category = ''))),
          for (final c in cats) Padding(padding: const EdgeInsets.symmetric(horizontal: 4), child: ChoiceChip(label: Text(c[lang] as String), selected: category == c['code'], onSelected: (_) => setState(() => category = c['code'] as String))),
        ])),
        Expanded(child: Async<dynamic>(ref.watch(_mastersProvider(query.entries.map((e) => '${e.key}=${e.value}').join('&'))), onRetry: () => ref.invalidate(_mastersProvider), data: (d) {
          final list = (d['content'] as List).cast<Json>();
          if (list.isEmpty) return Empty(s.t('noRequests'));
          return ListView.separated(padding: const EdgeInsets.all(16), itemCount: list.length, separatorBuilder: (_, __) => const SizedBox(height: 10), itemBuilder: (_, i) => _MasterCard(m: list[i]));
        })),
      ]),
    );
  }
}

final _mastersProvider = FutureProvider.family<dynamic, String>((ref, qs) async => ref.read(apiProvider).get('/api/v1/masters?$qs'));

class _MasterCard extends ConsumerWidget {
  final Json m;
  const _MasterCard({required this.m});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(sProvider);
    return InkWell(borderRadius: BorderRadius.circular(16), onTap: () => showModalBottomSheet(context: context, isScrollControlled: true, showDragHandle: true, builder: (_) => MasterSheet(m: m)), child: Panel(child: Row(children: [
      CircleAvatar(radius: 28, backgroundColor: T.navy, child: Text('${m['fullName']}'.substring(0, 1), style: const TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.w700))),
      const SizedBox(width: 12),
      Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [Flexible(child: Text('${m['fullName']}', overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700))), if (m['verified'] == true) const Padding(padding: EdgeInsets.only(left: 4), child: Icon(Icons.verified, size: 16, color: T.blue))]),
        Text('${m['specialization']}', style: const TextStyle(color: T.inkSoft)),
        Row(children: [const Icon(Icons.star, size: 15, color: T.orange), Text(' ${n(m['rating']).toStringAsFixed(1)} (${m['reviewsCount']}) · ${m['experienceYears']} ${s.t('years')}', style: const TextStyle(fontSize: 12))]),
        Text('${s.money(n(m['hourlyRate']))}${s.t('perHour')} · ${s.t(m['available'] == true ? 'onlineNow' : 'busy')}', style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
      ])),
    ])));
  }
}

class MasterSheet extends ConsumerWidget {
  final Json m;
  const MasterSheet({super.key, required this.m});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(sProvider);
    final reviews = ref.watch(FutureProvider.autoDispose<List>((r) async => (await r.read(apiProvider).get('/api/v1/masters/${m['id']}/reviews')) as List));
    return Padding(padding: const EdgeInsets.fromLTRB(20, 0, 20, 24), child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text('${m['fullName']}', style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
      Text('${m['specialization']}${m['companyName'] != null ? ' · ${m['companyName']}' : ''}', style: const TextStyle(color: T.inkSoft)),
      const SizedBox(height: 8),
      if (m['bio'] != null) Text('${m['bio']}'),
      const SizedBox(height: 12),
      Text(s.t('reviews'), style: const TextStyle(fontWeight: FontWeight.w700)),
      reviews.when(data: (l) => l.isEmpty ? Text(s.t('noReviews'), style: const TextStyle(color: T.inkSoft)) : Column(children: [for (final r in l.take(3)) ListTile(dense: true, contentPadding: EdgeInsets.zero, title: Text('${'★' * (r['rating'] as int)} ${r['authorName'] ?? ''}'), subtitle: Text('${r['body'] ?? ''}'))]), loading: () => const LinearProgressIndicator(), error: (e, _) => Text(errorText(e, s))),
      const SizedBox(height: 12),
      FilledButton.icon(icon: const Icon(Icons.chat_bubble_outline), label: Text(s.t('contact')), onPressed: () async {
        final nav = Navigator.of(context);
        try {
          final c = await ref.read(apiProvider).post('/api/v1/conversations/direct/${m['id']}');
          ref.invalidate(conversationsProvider);
          nav.pop();
          nav.push(MaterialPageRoute(builder: (_) => ChatScreen(conversationId: c['id'] as String, title: '${m['fullName']}')));
        } catch (e) { if (context.mounted) toast(context, errorText(e, s), error: true); }
      }),
    ]));
  }
}

/// ---------------- chat ----------------
class ChatListScreen extends ConsumerWidget {
  const ChatListScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(sProvider);
    final project = ref.watch(currentProjectProvider);
    return Scaffold(
      appBar: AppBar(title: Text(s.t('chat'))),
      floatingActionButton: project == null ? null : FloatingActionButton(tooltip: project['name'] as String, child: const Icon(Icons.forum_outlined), onPressed: () async {
        final nav = Navigator.of(context);
        try {
          final c = await ref.read(apiProvider).get('/api/v1/projects/${project['id']}/conversation');
          ref.invalidate(conversationsProvider);
          nav.push(MaterialPageRoute(builder: (_) => ChatScreen(conversationId: c['id'] as String, title: '${c['title']}')));
        } catch (e) { if (context.mounted) toast(context, errorText(e, s), error: true); }
      }),
      body: Async<List<Json>>(ref.watch(conversationsProvider), onRetry: () => ref.invalidate(conversationsProvider), data: (list) => list.isEmpty ? Empty(s.t('noChats')) : ListView(children: [
        for (final c in list) ListTile(
          leading: CircleAvatar(backgroundColor: T.navy, child: Text('${c['title']}'.substring(0, 1), style: const TextStyle(color: Colors.white))),
          title: Text('${c['title']}', style: const TextStyle(fontWeight: FontWeight.w700)),
          subtitle: Text('${(c['lastMessage'] as Json?)?['body'] ?? ''}', maxLines: 1, overflow: TextOverflow.ellipsis),
          trailing: n(c['unread']) > 0 ? CircleAvatar(radius: 11, backgroundColor: T.orange, child: Text('${c['unread']}', style: const TextStyle(color: Colors.white, fontSize: 12))) : null,
          onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => ChatScreen(conversationId: c['id'] as String, title: '${c['title']}'))),
        ),
      ])),
    );
  }
}

class ChatScreen extends ConsumerStatefulWidget {
  final String conversationId, title;
  const ChatScreen({super.key, required this.conversationId, required this.title});
  @override
  ConsumerState<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends ConsumerState<ChatScreen> {
  final ctrl = TextEditingController();
  bool sending = false;

  Future<void> _markRead() async {
    try {
      await ref.read(apiProvider).post('/api/v1/conversations/${widget.conversationId}/read');
      ref.invalidate(conversationsProvider);
    } catch (_) {}
  }

  @override
  void initState() {
    super.initState();
    Future.microtask(_markRead);
  }

  @override
  Widget build(BuildContext context) {
    final s = ref.watch(sProvider);
    final me = ref.watch(authProvider).user?['id'];
    final msgs = ref.watch(messagesProvider(widget.conversationId));
    ref.listen(messagesProvider(widget.conversationId), (_, next) { if (next.hasValue) _markRead(); });
    return Scaffold(
      appBar: AppBar(title: Text(widget.title), actions: const [RtDotLite()]),
      body: Column(children: [
        Expanded(child: Async<List<Json>>(msgs, onRetry: () => ref.invalidate(messagesProvider(widget.conversationId)), data: (list) => ListView.builder(reverse: true, padding: const EdgeInsets.all(12), itemCount: list.length, itemBuilder: (_, i) {
          final m = list[list.length - 1 - i];
          final mine = m['senderId'] == me;
          return Align(alignment: mine ? Alignment.centerRight : Alignment.centerLeft, child: Container(
            margin: const EdgeInsets.symmetric(vertical: 3), padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8), constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * .78),
            decoration: BoxDecoration(color: mine ? T.blue : T.surface, borderRadius: BorderRadius.circular(16), border: mine ? null : Border.all(color: T.line)),
            child: Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
              if (!mine) Align(alignment: Alignment.centerLeft, child: Text('${m['senderName']}', style: const TextStyle(color: T.orange, fontSize: 11, fontWeight: FontWeight.w700))),
              if (m['body'] != null) Align(alignment: Alignment.centerLeft, child: Text('${m['body']}', style: TextStyle(color: mine ? Colors.white : T.ink))),
              if (m['attachment'] != null) Padding(padding: const EdgeInsets.only(top: 4), child: ('${(m['attachment'] as Json)['contentType']}'.startsWith('image/')) ? ClipRRect(borderRadius: BorderRadius.circular(10), child: Image.network(mediaUrl((m['attachment'] as Json)['url'] as String), height: 160)) : Row(mainAxisSize: MainAxisSize.min, children: [Icon(Icons.attach_file, size: 16, color: mine ? Colors.white : T.ink), Text('${(m['attachment'] as Json)['filename']}', style: TextStyle(color: mine ? Colors.white : T.ink))])),
              Row(mainAxisSize: MainAxisSize.min, children: [Text(s.time(m['createdAt'] as String), style: TextStyle(fontSize: 10, color: mine ? Colors.white70 : T.inkSoft)), if (mine) Padding(padding: const EdgeInsets.only(left: 4), child: Icon(m['status'] == 'READ' ? Icons.done_all : Icons.done, size: 14, color: Colors.white70))]),
            ]),
          ));
        }))),
        SafeArea(child: Padding(padding: const EdgeInsets.fromLTRB(12, 4, 12, 8), child: Row(children: [
          Expanded(child: TextField(controller: ctrl, textInputAction: TextInputAction.send, onSubmitted: (_) => _send(), decoration: InputDecoration(hintText: s.t('message'), contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12), border: OutlineInputBorder(borderRadius: BorderRadius.circular(28))))),
          const SizedBox(width: 8),
          IconButton.filled(onPressed: sending ? null : _send, icon: const Icon(Icons.send), tooltip: s.t('send')),
        ]))),
      ]),
    );
  }

  Future<void> _send() async {
    final text = ctrl.text.trim();
    if (text.isEmpty) return;
    setState(() => sending = true);
    final ok = await act(context, ref, () => ref.read(apiProvider).post('/api/v1/conversations/${widget.conversationId}/messages', {'body': text}));
    if (ok) { ctrl.clear(); ref.invalidate(messagesProvider(widget.conversationId)); }
    if (mounted) setState(() => sending = false);
  }
}

class RtDotLite extends ConsumerWidget {
  const RtDotLite({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final st = ref.watch(rtProvider);
    return Padding(padding: const EdgeInsets.only(right: 16), child: Icon(Icons.circle, size: 10, color: st == RtState.online ? T.success : T.inkSoft));
  }
}

/// ---------------- profile ----------------
class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(sProvider);
    final u = ref.watch(authProvider).user;
    final lang = ref.watch(langProvider);
    return Scaffold(
      appBar: AppBar(title: Text(s.t('profile'))),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        Panel(child: Row(children: [CircleAvatar(radius: 28, backgroundColor: T.navy, child: Text('${u?['fullName'] ?? '?'}'.substring(0, 1), style: const TextStyle(color: Colors.white, fontSize: 22))), const SizedBox(width: 12), Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text('${u?['fullName']}', style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 17)), Text('${u?['phone']}', style: const TextStyle(color: T.inkSoft)), Text(s.t('${u?['role']}'), style: const TextStyle(color: T.blue, fontWeight: FontWeight.w600))]))])),
        const SizedBox(height: 12),
        Panel(title: s.t('language'), child: SegmentedButton<String>(segments: [for (final l in supportedLangs) ButtonSegment(value: l, label: Text(l.toUpperCase()))], selected: {lang}, onSelectionChanged: (v) => ref.read(langProvider.notifier).set(v.first))),
        const SizedBox(height: 12),
        OutlinedButton.icon(onPressed: () => ref.read(authProvider.notifier).logout(), icon: const Icon(Icons.logout), label: Text(s.t('logout'))),
      ]),
    );
  }
}
