'use client';
import { useQuery } from '@tanstack/react-query';
import { Bell, Building2, Calendar, Camera, ClipboardCheck, Calculator, CreditCard, FileText, FolderKanban, HardHat, LayoutDashboard, ListChecks, LogOut, Menu, MessageSquare, Package, Settings, ShieldCheck, Users, BarChart3, Inbox, X } from 'lucide-react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useState, type ReactNode } from 'react';
import { Logo } from './Logo';
import { StatusBadge } from './Status';
import { Button } from './ui';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { useI18n } from '@/lib/i18n';
import { useProject } from '@/lib/project';
import { useRealtime } from '@/lib/realtime';
import { cn } from '@/lib/utils';
import type { AppNotification, Page } from '@/lib/types';
import { fmtDateTime } from '@/lib/format';

const NAV = [
  { href: '/dashboard', key: 'nav.overview', icon: LayoutDashboard },
  { href: '/projects', key: 'nav.projects', icon: FolderKanban },
  { href: '/estimates', key: 'nav.estimates', icon: Calculator },
  { href: '/materials', key: 'nav.materials', icon: Package },
  { href: '/masters', key: 'nav.masters', icon: HardHat },
  { href: '/stages', key: 'nav.stages', icon: ListChecks },
  { href: '/calendar', key: 'nav.calendar', icon: Calendar },
  { href: '/photos', key: 'nav.photos', icon: Camera },
  { href: '/quality', key: 'nav.quality', icon: ShieldCheck },
  { href: '/messages', key: 'nav.messages', icon: MessageSquare },
  { href: '/documents', key: 'nav.documents', icon: FileText },
  { href: '/payments', key: 'nav.payments', icon: CreditCard },
  { href: '/reports', key: 'nav.reports', icon: BarChart3 },
  { href: '/settings', key: 'nav.settings', icon: Settings },
];

export function Shell({ children }: { children: ReactNode }) {
  const { user, ready, logout, isAdmin, isPro } = useAuth();
  const { t, lang, setLang, locale } = useI18n();
  const { projects, project, select } = useProject();
  const { state } = useRealtime();
  const path = usePathname();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [bell, setBell] = useState(false);

  useEffect(() => { if (ready && !user) router.replace('/login'); }, [ready, user, router]);
  useEffect(() => setOpen(false), [path]);

  const notes = useQuery({ queryKey: ['notifications'], queryFn: () => api.get<Page<AppNotification>>('/api/v1/notifications?size=15'), enabled: !!user });
  const unread = useQuery({ queryKey: ['notifications', 'unread'], queryFn: () => api.get<{ count: number }>('/api/v1/notifications/unread-count'), enabled: !!user, refetchInterval: 60_000 });
  if (!ready || !user) return <div className="grid min-h-screen place-items-center text-ink-soft">…</div>;

  const items = [...NAV];
  if (isPro) items.splice(2, 0, { href: '/requests', key: 'nav.requests', icon: Inbox });
  if (isAdmin) items.push({ href: '/admin', key: 'nav.admin', icon: Users });

  const nav = (
    <nav aria-label={t('nav.main')} className="flex-1 space-y-0.5 overflow-y-auto px-3 py-2">
      {items.map((i) => {
        const active = path === i.href || path.startsWith(`${i.href}/`);
        return (
          <Link key={i.href} href={i.href} aria-current={active ? 'page' : undefined}
            className={cn('flex items-center gap-3 rounded-control px-3 py-2 text-sm font-medium transition-colors', active ? 'bg-white/15 text-white' : 'text-white/70 hover:bg-white/10 hover:text-white')}>
            <i.icon className="h-[18px] w-[18px]" aria-hidden />{t(i.key)}
          </Link>
        );
      })}
    </nav>
  );
  const sidebarCard = project && (
    <div className="m-3 rounded-card bg-white/10 p-3 text-white">
      <div className="mb-1 flex items-center gap-2 text-xs text-white/70"><Building2 className="h-4 w-4" />{project.city}</div>
      <p className="line-clamp-2 text-sm font-semibold">{project.name}</p>
      <p className="mt-1 text-xs text-white/70">{project.areaM2} m² · {t(`status.${project.status}`)}</p>
    </div>
  );

  return (
    <div className="flex min-h-screen">
      <aside className="sticky top-0 hidden h-screen w-64 shrink-0 flex-col bg-navy lg:flex">
        <div className="px-5 py-5"><Logo light /></div>
        {nav}{sidebarCard}
      </aside>
      {open && (
        <div className="fixed inset-0 z-40 lg:hidden" role="dialog" aria-modal>
          <div className="absolute inset-0 bg-navy/50" onClick={() => setOpen(false)} />
          <aside className="absolute left-0 top-0 flex h-full w-72 flex-col bg-navy">
            <div className="flex items-center justify-between px-5 py-5"><Logo light /><button aria-label={t('common.close')} onClick={() => setOpen(false)} className="text-white"><X /></button></div>
            {nav}{sidebarCard}
          </aside>
        </div>
      )}
      <div className="min-w-0 flex-1">
        <header className="sticky top-0 z-30 flex items-center gap-3 border-b border-line/70 bg-canvas/90 px-4 py-3 backdrop-blur md:px-8">
          <Button variant="ghost" size="icon" className="lg:hidden" aria-label={t('nav.menu')} onClick={() => setOpen(true)}><Menu /></Button>
          <div className="min-w-0 flex-1">
            {projects.length > 0 ? (
              <select aria-label={t('nav.project')} value={project?.id} onChange={(e) => select(e.target.value)} className="max-w-full truncate rounded-control border border-line bg-surface px-3 py-2 text-sm font-semibold">
                {projects.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
              </select>
            ) : <span className="text-sm text-ink-soft">{t('nav.noProjects')}</span>}
            {project && <span className="ml-2 hidden align-middle sm:inline"><StatusBadge status={project.status} /></span>}
          </div>
          <span className={cn('hidden items-center gap-1.5 text-xs font-medium sm:flex', state === 'online' ? 'text-success' : 'text-ink-soft')} title={t(`rt.${state}`)}>
            <span className={cn('h-2 w-2 rounded-full', state === 'online' ? 'bg-success' : state === 'connecting' ? 'animate-pulse bg-warning' : 'bg-ink-soft/50')} />{t(`rt.${state}`)}
          </span>
          <div className="flex rounded-control border border-line bg-surface p-0.5 text-xs font-semibold" role="group" aria-label={t('settings.language')}>
            {(['kk', 'ru', 'en'] as const).map((l) => (
              <button key={l} onClick={() => setLang(l)} aria-pressed={lang === l} className={cn('rounded-[10px] px-2 py-1 uppercase', lang === l ? 'bg-navy text-white' : 'text-ink-soft')}>{l}</button>
            ))}
          </div>
          <div className="relative">
            <Button variant="ghost" size="icon" aria-label={t('nav.notifications')} onClick={() => setBell((b) => !b)}>
              <Bell className="h-5 w-5" />
              {!!unread.data?.count && <span className="absolute right-0.5 top-0.5 grid h-4 min-w-4 place-items-center rounded-full bg-accent px-1 text-[10px] font-bold text-white">{unread.data.count}</span>}
            </Button>
            {bell && (
              <div className="absolute right-0 mt-2 w-[min(92vw,360px)] rounded-card border border-line bg-surface p-3 shadow-pop">
                <div className="mb-2 flex items-center justify-between"><b className="text-sm">{t('nav.notifications')}</b>
                  <button className="text-xs text-brand" onClick={async () => { await api.post('/api/v1/notifications/read-all'); notes.refetch(); unread.refetch(); }}>{t('notif.readAll')}</button></div>
                <ul className="max-h-80 divide-y divide-line overflow-y-auto">
                  {(notes.data?.content ?? []).map((n) => (
                    <li key={n.id} className={cn('py-2 text-sm', !n.read && 'font-semibold')}><div>{n.title}</div>
                      <div className="text-xs font-normal text-ink-soft">{n.body} · {fmtDateTime(n.createdAt, locale)}</div></li>
                  ))}
                  {!notes.data?.content.length && <li className="py-6 text-center text-sm text-ink-soft">{t('notif.empty')}</li>}
                </ul>
              </div>
            )}
          </div>
          <div className="hidden items-center gap-2 md:flex">
            <div className="grid h-9 w-9 place-items-center rounded-full bg-navy text-sm font-bold text-white">{user.fullName.slice(0, 1)}</div>
            <div className="leading-tight"><div className="text-sm font-semibold">{user.fullName}</div><div className="text-xs text-ink-soft">{t(`role.${user.role}`)}</div></div>
          </div>
          <Button variant="ghost" size="icon" aria-label={t('auth.logout')} onClick={async () => { await logout(); router.replace('/login'); }}><LogOut className="h-5 w-5" /></Button>
        </header>
        <main className="mx-auto max-w-[1400px] px-4 py-6 md:px-8">{children}</main>
      </div>
    </div>
  );
}
