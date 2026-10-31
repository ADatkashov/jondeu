'use client';
import { Camera, X } from 'lucide-react';
import { useState } from 'react';
import { Badge, Button, Card, Dialog, EmptyState, ErrorState, PageHeader, Select, Skeleton } from '@/components/ui';
import { UploadDialog } from '@/features/Uploader';
import { absoluteUrl, api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { fmtDateTime } from '@/lib/format';
import { useAction, useMedia, useMembers, useStages } from '@/lib/hooks';
import { useI18n } from '@/lib/i18n';
import { useProject } from '@/lib/project';
import type { MediaItem } from '@/lib/types';

const CATS = ['BEFORE', 'DURING', 'AFTER', 'STAGE', 'INSPECTION'];

export default function PhotosPage() {
  const { t, locale } = useI18n();
  const { user } = useAuth();
  const { project, projectId } = useProject();
  const [f, setF] = useState({ category: '', stageId: '', uploaderId: '' });
  const q = useMedia({ ...f });
  const stages = useStages();
  const members = useMembers();
  const [upload, setUpload] = useState(false);
  const [open, setOpen] = useState<MediaItem | null>(null);
  const del = useAction((id: string) => api.del(`/api/v1/media/${id}`), { success: 'photos.deleted', invalidate: ['media'], onDone: () => setOpen(null) });
  if (!projectId) return <EmptyState title={t('dash.noProject')} />;
  const photos = (q.data ?? []).filter((m) => m.contentType.startsWith('image/'));
  return (
    <>
      <PageHeader title={t('nav.photos')} actions={<Button onClick={() => setUpload(true)}><Camera className="h-4 w-4" />{t('upload.title')}</Button>} />
      <Card className="mb-4 grid gap-3 sm:grid-cols-3">
        <Select aria-label={t('photos.category')} value={f.category} onChange={(e) => setF({ ...f, category: e.target.value })}><option value="">{t('photos.allCategories')}</option>{CATS.map((c) => <option key={c} value={c}>{t(`mcat.${c}`)}</option>)}</Select>
        <Select aria-label={t('photos.stage')} value={f.stageId} onChange={(e) => setF({ ...f, stageId: e.target.value })}><option value="">{t('photos.allStages')}</option>{stages.data?.map((s) => <option key={s.id} value={s.id}>{s.position}. {s.title}</option>)}</Select>
        <Select aria-label={t('photos.author')} value={f.uploaderId} onChange={(e) => setF({ ...f, uploaderId: e.target.value })}><option value="">{t('photos.allAuthors')}</option>{members.data?.map((m) => <option key={m.userId} value={m.userId}>{m.fullName}</option>)}</Select>
      </Card>
      {q.isLoading ? <Skeleton className="h-64" /> : q.isError ? <ErrorState error={q.error} onRetry={() => q.refetch()} /> : !photos.length ? <EmptyState title={t('photos.empty')} text={t('photos.emptyHint')} /> : (
        <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-5">
          {photos.map((m) => (
            <button key={m.id} onClick={() => setOpen(m)} className="group overflow-hidden rounded-card border border-line/70 bg-surface text-left shadow-card">
              <div className="aspect-[4/3] overflow-hidden bg-canvas">{/* eslint-disable-next-line @next/next/no-img-element */}<img src={absoluteUrl(m.url)} alt={m.caption ?? m.filename} loading="lazy" className="h-full w-full object-cover transition group-hover:scale-105" /></div>
              <div className="p-2.5"><Badge tone="blue">{t(`mcat.${m.category}`)}</Badge><p className="mt-1 truncate text-sm font-medium">{m.caption ?? m.filename}</p><p className="text-xs text-ink-soft">{fmtDateTime(m.createdAt, locale)}</p></div></button>
          ))}
        </div>
      )}
      <Dialog open={upload} onOpenChange={setUpload}>{upload && <UploadDialog categories={CATS} onClose={() => setUpload(false)} />}</Dialog>
      {open && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-navy/80 p-4" role="dialog" aria-modal onClick={() => setOpen(null)}>
          <div className="max-h-full max-w-4xl overflow-hidden rounded-card bg-surface" onClick={(e) => e.stopPropagation()}>
            {/* eslint-disable-next-line @next/next/no-img-element */}<img src={absoluteUrl(open.url)} alt={open.caption ?? open.filename} className="max-h-[70vh] w-full object-contain" />
            <div className="flex items-center justify-between gap-3 p-3"><div><b>{open.caption ?? open.filename}</b><div className="text-xs text-ink-soft">{t(`mcat.${open.category}`)} · {fmtDateTime(open.createdAt, locale)}</div></div>
              <div className="flex gap-2">{(open.uploaderId === user?.id || project?.myRole === 'OWNER') && <Button size="sm" variant="danger" loading={del.isPending} onClick={() => del.mutate(open.id)}>{t('common.delete')}</Button>}
                <Button size="icon" variant="ghost" aria-label={t('common.close')} onClick={() => setOpen(null)}><X /></Button></div></div>
          </div>
        </div>
      )}
    </>
  );
}
