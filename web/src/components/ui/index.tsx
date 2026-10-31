'use client';
import * as DialogPrimitive from '@radix-ui/react-dialog';
import { cva, type VariantProps } from 'class-variance-authority';
import { AlertTriangle, Inbox, Loader2, X } from 'lucide-react';
import { forwardRef, type ButtonHTMLAttributes, type HTMLAttributes, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react';
import { cn } from '@/lib/utils';
import { useT } from '@/lib/i18n';

const buttonVariants = cva(
  'inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-control text-sm font-semibold transition-colors disabled:pointer-events-none disabled:opacity-50',
  {
    variants: {
      variant: {
        primary: 'bg-brand text-white hover:bg-brand-600',
        accent: 'bg-accent text-white hover:bg-accent-600',
        outline: 'border border-line bg-surface text-ink hover:bg-canvas',
        ghost: 'text-ink hover:bg-canvas',
        danger: 'bg-danger text-white hover:opacity-90',
        success: 'bg-success text-white hover:opacity-90',
      },
      size: { sm: 'h-8 px-3', md: 'h-10 px-4', lg: 'h-12 px-6 text-base', icon: 'h-9 w-9' },
    },
    defaultVariants: { variant: 'primary', size: 'md' },
  },
);

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement>, VariantProps<typeof buttonVariants> { loading?: boolean }
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(({ className, variant, size, loading, children, disabled, type = 'button', ...p }, ref) => (
  <button ref={ref} type={type} className={cn(buttonVariants({ variant, size }), className)} disabled={disabled || loading} {...p}>
    {loading && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}{children}
  </button>
));
Button.displayName = 'Button';

export function Card({ className, ...p }: HTMLAttributes<HTMLDivElement>) { return <div className={cn('rounded-card border border-line/70 bg-surface p-5 shadow-card', className)} {...p} />; }
export function CardTitle({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return <div className="mb-3 flex items-center justify-between gap-2"><h3 className="text-[15px] font-semibold text-ink">{children}</h3>{action}</div>;
}

const tones = {
  neutral: 'bg-canvas text-ink-soft', blue: 'bg-brand-50 text-brand', green: 'bg-success-50 text-success', orange: 'bg-accent-50 text-accent-600',
  yellow: 'bg-warning-50 text-warning', red: 'bg-danger-50 text-danger', navy: 'bg-navy text-white',
} as const;
export type Tone = keyof typeof tones;
export function Badge({ tone = 'neutral', className, children }: { tone?: Tone; className?: string; children: ReactNode }) {
  return <span className={cn('inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold', tones[tone], className)}>{children}</span>;
}

const inputBase = 'w-full rounded-control border border-line bg-surface px-3 text-sm text-ink placeholder:text-ink-soft/70 focus:border-brand disabled:bg-canvas aria-[invalid=true]:border-danger';
export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(({ className, ...p }, ref) => <input ref={ref} className={cn(inputBase, 'h-10', className)} {...p} />);
Input.displayName = 'Input';
export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(({ className, ...p }, ref) => <textarea ref={ref} className={cn(inputBase, 'min-h-[88px] py-2', className)} {...p} />);
Textarea.displayName = 'Textarea';
export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(({ className, children, ...p }, ref) => <select ref={ref} className={cn(inputBase, 'h-10 pr-8', className)} {...p}>{children}</select>);
Select.displayName = 'Select';

export function Field({ label, error, children, hint }: { label: string; error?: string; children: ReactNode; hint?: string }) {
  const t = useT();
  return (
    <label className="block space-y-1.5">
      <span className="text-xs font-semibold text-ink-soft">{label}</span>
      {children}
      {hint && !error && <span className="block text-xs text-ink-soft">{hint}</span>}
      {error && <span role="alert" className="block text-xs text-danger">{t(error)}</span>}
    </label>
  );
}

export function Progress({ value, tone = 'brand', className }: { value: number; tone?: 'brand' | 'success' | 'accent' | 'danger'; className?: string }) {
  const bar = { brand: 'bg-brand', success: 'bg-success', accent: 'bg-accent', danger: 'bg-danger' }[tone];
  const v = Math.max(0, Math.min(100, value));
  return (
    <div role="progressbar" aria-valuenow={Math.round(v)} aria-valuemin={0} aria-valuemax={100} className={cn('h-2 w-full overflow-hidden rounded-full bg-canvas', className)}>
      <div className={cn('h-full rounded-full transition-all duration-500', bar)} style={{ width: `${v}%` }} />
    </div>
  );
}

export function Skeleton({ className }: { className?: string }) { return <div className={cn('animate-pulse rounded-control bg-line/60', className)} aria-hidden />; }

export function EmptyState({ title, text, action }: { title: string; text?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 rounded-card border border-dashed border-line px-6 py-10 text-center">
      <Inbox className="h-8 w-8 text-ink-soft/60" aria-hidden />
      <p className="font-semibold">{title}</p>
      {text && <p className="max-w-sm text-sm text-ink-soft">{text}</p>}
      {action}
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const t = useT();
  return (
    <div role="alert" className="flex flex-col items-center gap-2 rounded-card border border-danger/30 bg-danger-50 px-6 py-8 text-center">
      <AlertTriangle className="h-6 w-6 text-danger" aria-hidden />
      <p className="text-sm">{error instanceof Error ? error.message : t('common.error')}</p>
      {onRetry && <Button size="sm" variant="outline" onClick={onRetry}>{t('common.retry')}</Button>}
    </div>
  );
}

export const Dialog = DialogPrimitive.Root;
export const DialogTrigger = DialogPrimitive.Trigger;
export function DialogContent({ title, children, wide }: { title: string; children: ReactNode; wide?: boolean }) {
  const t = useT();
  return (
    <DialogPrimitive.Portal>
      <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-navy/40 backdrop-blur-[1px]" />
      <DialogPrimitive.Content className={cn('fixed left-1/2 top-1/2 z-50 max-h-[90vh] w-[min(94vw,var(--w))] -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-card bg-surface p-6 shadow-pop')} style={{ ['--w' as string]: wide ? '760px' : '520px' }}>
        <div className="mb-4 flex items-start justify-between gap-4">
          <DialogPrimitive.Title className="text-lg font-bold">{title}</DialogPrimitive.Title>
          <DialogPrimitive.Close className="rounded-full p-1 text-ink-soft hover:bg-canvas" aria-label={t('common.close')}><X className="h-5 w-5" /></DialogPrimitive.Close>
        </div>
        <DialogPrimitive.Description className="sr-only">{title}</DialogPrimitive.Description>
        {children}
      </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
  );
}
export const DialogClose = DialogPrimitive.Close;

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
      <div><h1 className="text-2xl font-bold tracking-tight">{title}</h1>{subtitle && <p className="mt-0.5 text-sm text-ink-soft">{subtitle}</p>}</div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

export function StatCard({ label, value, sub, tone, icon }: { label: string; value: ReactNode; sub?: ReactNode; tone?: 'green' | 'orange' | 'red'; icon?: ReactNode }) {
  return (
    <Card className="flex flex-col gap-1.5 p-4">
      <div className="flex items-center justify-between text-xs font-semibold text-ink-soft"><span>{label}</span>{icon}</div>
      <div className={cn('tabular whitespace-nowrap text-[17px] font-bold leading-tight', tone === 'green' && 'text-success', tone === 'orange' && 'text-accent-600', tone === 'red' && 'text-danger')}>{value}</div>
      {sub && <div className="text-xs text-ink-soft">{sub}</div>}
    </Card>
  );
}
