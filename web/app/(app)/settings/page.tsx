'use client';
import { useState } from 'react';
import { Badge, Button, Card, CardTitle, Field, Input, PageHeader, Select } from '@/components/ui';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { fmtPhone } from '@/lib/format';
import { useAction } from '@/lib/hooks';
import { useI18n } from '@/lib/i18n';
import type { User } from '@/lib/types';

export default function SettingsPage() {
  const { t, lang, setLang } = useI18n();
  const { user, logout } = useAuth();
  const [name, setName] = useState(user?.fullName ?? '');
  const [city, setCity] = useState(user?.city ?? 'Almaty');
  const save = useAction(() => api.patch<User>('/api/v1/auth/me', { fullName: name, city, language: lang }), { success: 'settings.saved' });
  const out = useAction(() => api.post('/api/v1/auth/logout-all'), { success: 'settings.signedOut', onDone: () => logout() });
  if (!user) return null;
  return (
    <>
      <PageHeader title={t('nav.settings')} />
      <div className="grid gap-4 lg:grid-cols-2">
        <Card><CardTitle>{t('settings.profile')}</CardTitle><div className="space-y-3">
          <Field label={t('auth.fullName')}><Input value={name} onChange={(e) => setName(e.target.value)} /></Field>
          <Field label={t('auth.phone')}><Input value={fmtPhone(user.phone)} disabled /></Field>
          <Field label={t('settings.city')}><Select value={city} onChange={(e) => setCity(e.target.value)}><option value="Almaty">Алматы</option><option value="Astana">Астана</option><option value="Shymkent">Шымкент</option></Select></Field>
          <Field label={t('settings.language')}><Select value={lang} onChange={(e) => setLang(e.target.value as 'ru' | 'kk' | 'en')}><option value="kk">Қазақша</option><option value="ru">Русский</option><option value="en">English</option></Select></Field>
          <div className="flex items-center justify-between"><Badge tone="blue">{t(`role.${user.role}`)}</Badge><Button loading={save.isPending} onClick={() => save.mutate(undefined)}>{t('common.save')}</Button></div></div></Card>
        <Card><CardTitle>{t('settings.security')}</CardTitle><p className="mb-3 text-sm text-ink-soft">{t('settings.securityHint')}</p>
          <Button variant="danger" loading={out.isPending} onClick={() => out.mutate(undefined)}>{t('settings.signOutAll')}</Button></Card>
      </div>
    </>
  );
}
