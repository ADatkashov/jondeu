'use client';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Check, CheckCheck, Paperclip, Send } from 'lucide-react';
import { useSearchParams } from 'next/navigation';
import { Suspense, useEffect, useRef, useState } from 'react';
import { Card, EmptyState, ErrorState, PageHeader, Skeleton, Button } from '@/components/ui';
import { absoluteUrl, api, ApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { fmtTime } from '@/lib/format';
import { errorText } from '@/lib/hooks';
import { useI18n } from '@/lib/i18n';
import { useProject } from '@/lib/project';
import { useToast } from '@/lib/toast';
import type { ChatMessage, Conversation } from '@/lib/types';
import { cn } from '@/lib/utils';

function Chat() {
  const { t, locale } = useI18n();
  const { user } = useAuth();
  const { projectId } = useProject();
  const qc = useQueryClient();
  const toast = useToast();
  const params = useSearchParams();
  const [sel, setSel] = useState<string | undefined>(params.get('c') ?? undefined);
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const file = useRef<HTMLInputElement>(null);
  const bottom = useRef<HTMLDivElement>(null);

  // make sure the active project's conversation exists (members are synced server-side)
  useQuery({ queryKey: ['project-conv', projectId], queryFn: async () => { const c = await api.get<Conversation>(`/api/v1/projects/${projectId}/conversation`); qc.invalidateQueries({ queryKey: ['conversations'] }); return c; }, enabled: !!projectId });
  const convs = useQuery({ queryKey: ['conversations'], queryFn: () => api.get<Conversation[]>('/api/v1/conversations') });
  const current = sel ?? convs.data?.find((c) => c.projectId === projectId)?.id ?? convs.data?.[0]?.id;
  const msgs = useQuery({ queryKey: ['messages', current], queryFn: async () => (await api.get<ChatMessage[]>(`/api/v1/conversations/${current}/messages?limit=100`)).reverse(), enabled: !!current });
  useEffect(() => { if (current && msgs.data) { api.post(`/api/v1/conversations/${current}/read`).then(() => qc.invalidateQueries({ queryKey: ['conversations'] })).catch(() => {}); } }, [current, msgs.data?.length]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => bottom.current?.scrollIntoView({ behavior: 'smooth' }), [msgs.data?.length]);

  const send = async (body?: string, mediaId?: string) => {
    if (!current || (!body?.trim() && !mediaId)) return;
    setSending(true);
    try { await api.post(`/api/v1/conversations/${current}/messages`, { body, mediaId }); setText(''); await msgs.refetch(); }
    catch (e) { toast(e instanceof ApiError ? errorText(e, t) : t('common.error'), 'error'); } finally { setSending(false); }
  };
  const attach = async (f?: File) => {
    if (!f || !current) return;
    try { const a = await api.upload<{ id: string }>(`/api/v1/conversations/${current}/attachments`, f); await send(undefined, a.id); }
    catch (e) { toast(e instanceof ApiError ? errorText(e, t) : t('common.error'), 'error'); }
  };
  const conv = convs.data?.find((c) => c.id === current);
  return (
    <div className="grid h-[calc(100vh-190px)] min-h-[480px] gap-4 lg:grid-cols-[300px_1fr]">
      <Card className="overflow-y-auto p-2">
        {convs.isLoading ? <Skeleton className="h-24" /> : !convs.data?.length ? <p className="p-4 text-sm text-ink-soft">{t('chat.noConversations')}</p> : convs.data.map((c) => (
          <button key={c.id} onClick={() => setSel(c.id)} className={cn('flex w-full items-start gap-3 rounded-control p-3 text-left', c.id === current ? 'bg-brand-50' : 'hover:bg-canvas')}>
            <div className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-navy text-sm font-bold text-white">{c.title.slice(0, 1)}</div>
            <div className="min-w-0 flex-1"><div className="flex justify-between gap-2"><b className="truncate text-sm">{c.title}</b>{c.unread > 0 && <span className="rounded-full bg-accent px-2 text-xs font-bold text-white">{c.unread}</span>}</div>
              <p className="truncate text-xs text-ink-soft">{c.lastMessage?.body ?? (c.lastMessage?.attachment ? `📎 ${c.lastMessage.attachment.filename}` : '')}</p></div></button>))}
      </Card>
      <Card className="flex min-h-0 flex-col p-0">
        {!current ? <div className="grid flex-1 place-items-center"><EmptyState title={t('chat.select')} /></div> : (<>
          <div className="border-b border-line px-5 py-3 font-semibold">{conv?.title}</div>
          <div className="flex-1 space-y-2 overflow-y-auto bg-canvas/50 p-4" aria-live="polite">
            {msgs.isLoading ? <Skeleton className="h-20" /> : msgs.isError ? <ErrorState error={msgs.error} /> : msgs.data?.map((m) => {
              const mine = m.senderId === user?.id;
              return (
                <div key={m.id} className={cn('flex', mine ? 'justify-end' : 'justify-start')}>
                  <div className={cn('max-w-[78%] rounded-2xl px-3.5 py-2 text-sm shadow-sm', mine ? 'rounded-br-md bg-brand text-white' : 'rounded-bl-md bg-surface')}>
                    {!mine && <div className="text-xs font-semibold text-accent-600">{m.senderName}</div>}
                    {m.body && <p className="whitespace-pre-wrap break-words">{m.body}</p>}
                    {m.attachment && (m.attachment.contentType.startsWith('image/')
                      // eslint-disable-next-line @next/next/no-img-element
                      ? <a href={absoluteUrl(m.attachment.url)} target="_blank" rel="noreferrer"><img src={absoluteUrl(m.attachment.url)} alt={m.attachment.filename} className="mt-1 max-h-56 rounded-lg" /></a>
                      : <a href={absoluteUrl(m.attachment.url)} target="_blank" rel="noreferrer" className={cn('mt-1 flex items-center gap-2 rounded-lg p-2', mine ? 'bg-white/15' : 'bg-canvas')}><Paperclip className="h-4 w-4" />{m.attachment.filename}</a>)}
                    <div className={cn('mt-0.5 flex items-center justify-end gap-1 text-[11px]', mine ? 'text-white/70' : 'text-ink-soft')}>{fmtTime(m.createdAt, locale)}{mine && (m.status === 'READ' ? <CheckCheck className="h-3.5 w-3.5" aria-label={t('chat.read')} /> : <Check className="h-3.5 w-3.5" aria-label={t('chat.sent')} />)}</div>
                  </div></div>);
            })}
            {msgs.data?.length === 0 && <p className="py-10 text-center text-sm text-ink-soft">{t('chat.empty')}</p>}
            <div ref={bottom} />
          </div>
          <form className="flex items-center gap-2 border-t border-line p-3" onSubmit={(e) => { e.preventDefault(); send(text); }}>
            <input ref={file} type="file" hidden accept="image/jpeg,image/png,image/webp,application/pdf" onChange={(e) => { attach(e.target.files?.[0]); e.target.value = ''; }} />
            <Button variant="ghost" size="icon" aria-label={t('chat.attach')} onClick={() => file.current?.click()}><Paperclip className="h-5 w-5" /></Button>
            <input value={text} onChange={(e) => setText(e.target.value)} maxLength={5000} placeholder={t('chat.placeholder')} aria-label={t('chat.placeholder')} className="h-11 flex-1 rounded-full border border-line bg-surface px-4 text-sm" />
            <Button type="submit" size="icon" className="h-11 w-11 rounded-full" loading={sending} aria-label={t('common.send')} disabled={!text.trim()}><Send className="h-4 w-4" /></Button>
          </form></>)}
      </Card>
    </div>
  );
}

export default function MessagesPage() {
  const { t } = useI18n();
  return <><PageHeader title={t('nav.messages')} /><Suspense fallback={<Skeleton className="h-96" />}><Chat /></Suspense></>;
}
