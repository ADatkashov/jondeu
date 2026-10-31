'use client';
import { Upload } from 'lucide-react';
import { useRef, useState } from 'react';
import { Button, DialogContent, Field, Input, Select } from '@/components/ui';
import { api } from '@/lib/api';
import { useAction, useStages } from '@/lib/hooks';
import { useI18n } from '@/lib/i18n';
import { useProject } from '@/lib/project';

const MAX = 15 * 1024 * 1024;
const OK = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf'];

/** Client-side pre-validation only improves UX; the backend re-validates type (magic bytes) and size. */
export function UploadDialog({ categories, onClose, defaultCategory }: { categories: string[]; onClose: () => void; defaultCategory?: string }) {
  const { t } = useI18n();
  const { projectId } = useProject();
  const stages = useStages();
  const ref = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [category, setCategory] = useState(defaultCategory ?? categories[0]);
  const [stageId, setStageId] = useState('');
  const [caption, setCaption] = useState('');
  const up = useAction(() => api.upload(`/api/v1/projects/${projectId}/media`, file!, { category, stageId, caption }), { success: 'upload.done', invalidate: ['media'], onDone: onClose });
  const pick = (f?: File) => {
    setErr(null);
    if (!f) return;
    if (!OK.includes(f.type)) return setErr(t('upload.badType'));
    if (f.size > MAX) return setErr(t('upload.tooBig'));
    setFile(f);
  };
  return (
    <DialogContent title={t('upload.title')}>
      <div className="space-y-3">
        <button type="button" onClick={() => ref.current?.click()} onDragOver={(e) => e.preventDefault()} onDrop={(e) => { e.preventDefault(); pick(e.dataTransfer.files[0]); }}
          className="flex w-full flex-col items-center gap-2 rounded-card border-2 border-dashed border-line p-6 text-sm text-ink-soft hover:border-brand">
          <Upload className="h-6 w-6" />{file ? <b className="text-ink">{file.name}</b> : t('upload.drop')}<span className="text-xs">JPEG · PNG · WebP · PDF · ≤ 15 MB</span></button>
        <input ref={ref} type="file" hidden accept={OK.join(',')} onChange={(e) => pick(e.target.files?.[0])} />
        {err && <p role="alert" className="text-sm text-danger">{err}</p>}
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={t('photos.category')}><Select value={category} onChange={(e) => setCategory(e.target.value)}>{categories.map((c) => <option key={c} value={c}>{t(`mcat.${c}`)}</option>)}</Select></Field>
          <Field label={t('photos.stage')}><Select value={stageId} onChange={(e) => setStageId(e.target.value)}><option value="">—</option>{stages.data?.map((s) => <option key={s.id} value={s.id}>{s.position}. {s.title}</option>)}</Select></Field>
        </div>
        <Field label={t('photos.caption')}><Input value={caption} onChange={(e) => setCaption(e.target.value)} maxLength={300} /></Field>
      </div>
      <div className="mt-4 flex justify-end gap-2"><Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button><Button disabled={!file} loading={up.isPending} onClick={() => up.mutate(undefined)}>{t('upload.send')}</Button></div>
    </DialogContent>
  );
}
