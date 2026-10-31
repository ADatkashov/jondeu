'use client';
import { useQuery } from '@tanstack/react-query';
import { BadgeCheck, MapPin, MessageSquare, Search, Star } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { Badge, Button, Card, Dialog, DialogContent, EmptyState, ErrorState, Input, PageHeader, Select, Skeleton } from '@/components/ui';
import { api } from '@/lib/api';
import { fmtMoney } from '@/lib/format';
import { useCategories } from '@/lib/hooks';
import { useI18n } from '@/lib/i18n';
import type { Conversation, Master, Page } from '@/lib/types';

function Detail({ m, onClose }: { m: Master; onClose: () => void }) {
  const { t, locale } = useI18n();
  const router = useRouter();
  const reviews = useQuery({ queryKey: ['reviews', m.id], queryFn: () => api.get<{ id: string; authorName?: string; rating: number; body?: string; createdAt: string }[]>(`/api/v1/masters/${m.id}/reviews`) });
  const chat = async () => { const c = await api.post<Conversation>(`/api/v1/conversations/direct/${m.id}`); onClose(); router.push(`/messages?c=${c.id}`); };
  return (
    <DialogContent title={m.fullName} wide>
      <div className="flex flex-wrap items-center gap-2"><Badge tone="blue">{m.specialization}</Badge>{m.verified && <Badge tone="green"><BadgeCheck className="h-3.5 w-3.5" />{t('masters.verified')}</Badge>}{m.companyName && <Badge>{m.companyName}</Badge>}</div>
      <p className="mt-3 text-sm">{m.bio}</p>
      <div className="mt-4 grid grid-cols-2 gap-3 text-center sm:grid-cols-4">
        {[[t('masters.rating'), `${Number(m.rating).toFixed(1)} ★ (${m.reviewsCount})`], [t('masters.experience'), t('masters.years', { n: m.experienceYears })], [t('masters.completed'), m.completedProjects], [t('masters.rate'), m.hourlyRate ? `${fmtMoney(m.hourlyRate)}/${t('masters.hour')}` : '—']].map(([k, v]) => <div key={k as string} className="rounded-control bg-canvas p-3"><div className="text-xs text-ink-soft">{k}</div><div className="font-bold">{v}</div></div>)}
      </div>
      <h4 className="mb-2 mt-5 font-semibold">{t('masters.reviews')}</h4>
      <ul className="max-h-48 space-y-2 overflow-y-auto">{reviews.data?.map((r) => <li key={r.id} className="rounded-control bg-canvas p-3 text-sm"><b>{r.authorName}</b> · {'★'.repeat(r.rating)}<p>{r.body}</p><span className="text-xs text-ink-soft">{new Date(r.createdAt).toLocaleDateString(locale)}</span></li>)}
        {!reviews.data?.length && <li className="text-sm text-ink-soft">{t('masters.noReviews')}</li>}</ul>
      <div className="mt-5 flex justify-end"><Button onClick={chat}><MessageSquare className="h-4 w-4" />{t('masters.contact')}</Button></div>
    </DialogContent>
  );
}

export default function MastersPage() {
  const { t, lang } = useI18n();
  const cats = useCategories();
  const [f, setF] = useState({ q: '', category: '', sort: 'recommended', minRating: '', verified: '', available: '', maxRate: '' });
  const [open, setOpen] = useState<Master | null>(null);
  const qs = new URLSearchParams(Object.entries(f).filter(([, v]) => v)).toString();
  const q = useQuery({ queryKey: ['masters', qs], queryFn: () => api.get<Page<Master>>(`/api/v1/masters?size=30&${qs}`) });
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });
  return (
    <>
      <PageHeader title={t('nav.masters')} subtitle={t('masters.subtitle')} />
      <Card className="mb-4 grid gap-3 md:grid-cols-3 xl:grid-cols-6">
        <div className="relative xl:col-span-2"><Search className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-ink-soft" /><Input className="pl-9" placeholder={t('masters.search')} aria-label={t('masters.search')} value={f.q} onChange={set('q')} /></div>
        <Select aria-label={t('wizard.category')} value={f.category} onChange={set('category')}><option value="">{t('masters.allCategories')}</option>{cats.data?.map((c) => <option key={c.code} value={c.code}>{c[lang]}</option>)}</Select>
        <Select aria-label={t('masters.sort')} value={f.sort} onChange={set('sort')}>{['recommended', 'rating', 'price', 'experience'].map((s) => <option key={s} value={s}>{t(`sort.${s}`)}</option>)}</Select>
        <Select aria-label={t('masters.rating')} value={f.minRating} onChange={set('minRating')}><option value="">{t('masters.anyRating')}</option><option value="4.5">4.5+</option><option value="4.8">4.8+</option></Select>
        <Select aria-label={t('masters.availability')} value={f.available} onChange={set('available')}><option value="">{t('masters.anyAvail')}</option><option value="true">{t('masters.availableNow')}</option></Select>
        <Select aria-label={t('masters.verified')} value={f.verified} onChange={set('verified')}><option value="">{t('masters.anyVerif')}</option><option value="true">{t('masters.verifiedOnly')}</option></Select>
        <Select aria-label={t('masters.rate')} value={f.maxRate} onChange={set('maxRate')}><option value="">{t('masters.anyPrice')}</option><option value="1600">≤ 1 600 ₸</option><option value="2000">≤ 2 000 ₸</option><option value="3500">≤ 3 500 ₸</option></Select>
      </Card>
      {q.isLoading ? <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">{[...Array(6)].map((_, i) => <Skeleton key={i} className="h-36" />)}</div> : q.isError ? <ErrorState error={q.error} onRetry={() => q.refetch()} /> : !q.data?.content.length ? <EmptyState title={t('masters.empty')} text={t('masters.emptyHint')} /> : (
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">{q.data.content.map((m) => (
          <button key={m.id} onClick={() => setOpen(m)} className="rounded-card border border-line/70 bg-surface p-4 text-left shadow-card transition hover:shadow-pop">
            <div className="flex gap-3"><div className="grid h-14 w-14 shrink-0 place-items-center rounded-full bg-navy text-xl font-bold text-white">{m.fullName.slice(0, 1)}</div>
              <div className="min-w-0 flex-1"><div className="flex items-center gap-1.5"><b className="truncate">{m.fullName}</b>{m.verified && <BadgeCheck className="h-4 w-4 shrink-0 text-brand" aria-label={t('masters.verified')} />}</div>
                <div className="text-sm text-ink-soft">{m.specialization}</div>
                <div className="mt-1 flex items-center gap-3 text-xs"><span className="flex items-center gap-1 font-semibold"><Star className="h-3.5 w-3.5 fill-accent text-accent" />{Number(m.rating).toFixed(1)} <span className="font-normal text-ink-soft">({m.reviewsCount})</span></span>
                  <span className="flex items-center gap-1 text-ink-soft"><MapPin className="h-3.5 w-3.5" />{m.district ?? m.city}</span></div></div></div>
            <div className="mt-3 flex items-center justify-between text-sm"><span className="font-semibold">{m.hourlyRate ? `${fmtMoney(m.hourlyRate)}/${t('masters.hour')}` : '—'}</span><Badge tone={m.available ? 'green' : 'neutral'}>{t(m.available ? 'masters.availableNow' : 'masters.busy')}</Badge></div>
            <div className="mt-1 text-xs text-ink-soft">{t('masters.years', { n: m.experienceYears })} · {t('masters.projects', { n: m.completedProjects })}</div></button>
        ))}</div>
      )}
      <Dialog open={!!open} onOpenChange={(o) => !o && setOpen(null)}>{open && <Detail m={open} onClose={() => setOpen(null)} />}</Dialog>
    </>
  );
}
