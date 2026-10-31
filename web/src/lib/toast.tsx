'use client';
import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';
import { CheckCircle2, Info, XCircle } from 'lucide-react';

type Kind = 'success' | 'error' | 'info';
interface Toast { id: number; kind: Kind; text: string }
const Ctx = createContext<(text: string, kind?: Kind) => void>(() => {});

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<Toast[]>([]);
  const push = useCallback((text: string, kind: Kind = 'info') => {
    const id = Date.now() + Math.random();
    setItems((p) => [...p.slice(-3), { id, kind, text }]);
    setTimeout(() => setItems((p) => p.filter((x) => x.id !== id)), 4500);
  }, []);
  return (
    <Ctx.Provider value={push}>
      {children}
      <div role="status" aria-live="polite" className="pointer-events-none fixed bottom-4 right-4 z-[100] flex w-[min(92vw,380px)] flex-col gap-2">
        {items.map((t) => (
          <div key={t.id} className="pointer-events-auto flex items-start gap-3 rounded-control border border-line bg-surface p-3 text-sm shadow-pop">
            {t.kind === 'success' ? <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-success" /> : t.kind === 'error' ? <XCircle className="mt-0.5 h-4 w-4 shrink-0 text-danger" /> : <Info className="mt-0.5 h-4 w-4 shrink-0 text-brand" />}
            <span>{t.text}</span>
          </div>
        ))}
      </div>
    </Ctx.Provider>
  );
}
export const useToast = () => useContext(Ctx);
