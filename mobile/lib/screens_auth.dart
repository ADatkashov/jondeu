import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/i18n.dart';
import 'core/session.dart';
import 'core/tokens.dart';
import 'ui.dart';

/// Kazakhstan mobile number normalisation — mirrors the backend rule (+7XXXXXXXXXX).
String? normalizePhone(String raw) {
  var d = raw.replaceAll(RegExp(r'\D'), '');
  if (d.length == 11 && (d.startsWith('8') || d.startsWith('7'))) d = d.substring(1);
  return d.length == 10 && (d.startsWith('7') || d.startsWith('6')) ? '+7$d' : null;
}

class AuthScreen extends ConsumerStatefulWidget {
  const AuthScreen({super.key});
  @override
  ConsumerState<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends ConsumerState<AuthScreen> {
  final _form = GlobalKey<FormState>();
  final _phone = TextEditingController(text: '+7');
  final _pass = TextEditingController();
  final _name = TextEditingController();
  bool _register = false, _terms = false, _busy = false;
  String _role = 'CUSTOMER';

  Future<void> _submit() async {
    if (!_form.currentState!.validate()) return;
    setState(() => _busy = true);
    final phone = normalizePhone(_phone.text)!;
    final ok = await act(context, ref, () => _register
        ? ref.read(authProvider.notifier).register({'phone': phone, 'password': _pass.text, 'fullName': _name.text.trim(), 'role': _role})
        : ref.read(authProvider.notifier).login(phone, _pass.text));
    if (mounted && !ok) setState(() => _busy = false);
  }

  @override
  Widget build(BuildContext context) {
    final s = ref.watch(sProvider);
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Form(
                key: _form,
                child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                  Row(mainAxisAlignment: MainAxisAlignment.end, children: [
                    for (final l in supportedLangs)
                      TextButton(onPressed: () => ref.read(langProvider.notifier).set(l), child: Text(l.toUpperCase(), style: TextStyle(fontWeight: ref.watch(langProvider) == l ? FontWeight.w800 : FontWeight.w400, color: ref.watch(langProvider) == l ? T.navy : T.inkSoft))),
                  ]),
                  const Row(children: [Icon(Icons.home_rounded, color: T.orange, size: 36), SizedBox(width: 8), Text('JONDEU', style: TextStyle(fontSize: 28, fontWeight: FontWeight.w900, color: T.navy))]),
                  const SizedBox(height: 4),
                  Text(s.t('tagline'), style: const TextStyle(color: T.inkSoft, fontSize: 16)),
                  const SizedBox(height: 28),
                  if (_register) ...[
                    TextFormField(controller: _name, decoration: InputDecoration(labelText: s.t('fullName')), validator: (v) => (v ?? '').trim().length < 2 ? s.t('valRequired') : null),
                    const SizedBox(height: 12),
                    DropdownButtonFormField<String>(initialValue: _role, decoration: InputDecoration(labelText: s.t('iAm')), items: [for (final r in ['CUSTOMER', 'CONTRACTOR', 'MASTER', 'COMPANY_ADMIN']) DropdownMenuItem(value: r, child: Text(s.t(r)))], onChanged: (v) => setState(() => _role = v!)),
                    const SizedBox(height: 12),
                  ],
                  TextFormField(controller: _phone, keyboardType: TextInputType.phone, autofillHints: const [AutofillHints.telephoneNumber], decoration: InputDecoration(labelText: s.t('phone')), validator: (v) => normalizePhone(v ?? '') == null ? s.t('valPhone') : null),
                  const SizedBox(height: 12),
                  TextFormField(controller: _pass, obscureText: true, decoration: InputDecoration(labelText: s.t('password')), validator: (v) => (v ?? '').length < (_register ? 8 : 1) ? s.t('valPassword') : null),
                  if (_register) CheckboxListTile(contentPadding: EdgeInsets.zero, controlAffinity: ListTileControlAffinity.leading, value: _terms, onChanged: (v) => setState(() => _terms = v ?? false), title: Text(s.t('terms'), style: const TextStyle(fontSize: 13))),
                  const SizedBox(height: 16),
                  FilledButton(onPressed: _busy || (_register && !_terms) ? null : _submit, child: _busy ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white)) : Text(s.t(_register ? 'register' : 'login'))),
                  TextButton(onPressed: () => setState(() => _register = !_register), child: Text(s.t(_register ? 'haveAccount' : 'noAccount'))),
                ]),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
