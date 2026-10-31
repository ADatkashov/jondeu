'use client';
import { FileText, Upload } from 'lucide-react';
import { useState } from 'react';
import { Badge, Button, Card, Dialog, EmptyState, ErrorState, PageHeader, Skeleton } from '@/components/ui';
import { UploadDialog } from '@/features/Uploader';
import { absoluteUrl } from '@/lib/api';
import { fmtDateTime } from '@/lib/format';
import { useMedia } from '@/lib/hooks';
import { useI18n } from '@/lib/i18n';
import { useProject } from '@/lib/project';

export default function DocumentsPage() {
  const { t, locale } = useI18n();
  const { projectId } = useProject();
  const docs = useMedia({ category: 'DOCUMENT' });
  const plans = useMedia({ category: 'PLAN' });
  const [open, setOpen] = useState(false);
  if (!projectId) return <EmptyState title={t('dash.noProject')} />;
  const all = [...(docs.data ?? []), ...(plans.data ?? [])].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  return (
    <>
      <PageHeader title={t('nav.documents')} actions={<Button onClick={() => setOpen(true)}><Upload className="h-4 w-4" />{t('upload.title')}</Button>} />
      {docs.isLoading ? <Skeleton className="h-40" /> : docs.isError ? <ErrorState error={docs.error} onRetry={() => docs.refetch()} /> : !all.length ? <EmptyState title={t('docs.empty')} text={t('docs.emptyHint')} /> : (
        <Card className="p-0"><ul className="divide-y divide-line">{all.map((d) => (
          <li key={d.id} className="flex items-center gap-3 p-4"><FileText className="h-8 w-8 text-brand" /><div className="min-w-0 flex-1"><a className="block truncate font-medium text-brand hover:underline" href={absoluteUrl(d.url)} target="_blank" rel="noreferrer">{d.caption ?? d.filename}</a>
            <div className="text-xs text-ink-soft">{d.filename} · {(d.sizeBytes / 1024).toFixed(0)} KB · {fmtDateTime(d.createdAt, locale)}</div></div><Badge>{t(`mcat.${d.category}`)}</Badge></li>))}</ul></Card>
      )}
      <Dialog open={open} onOpenChange={setOpen}>{open && <UploadDialog categories={['DOCUMENT', 'PLAN']} onClose={() => setOpen(false)} />}</Dialog>
    </>
  );
}
