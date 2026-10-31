'use client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { ApiError } from '@/lib/api';
import { AuthProvider } from '@/lib/auth';
import { I18nProvider } from '@/lib/i18n';
import { ToastProvider } from '@/lib/toast';

export function Providers({ children }: { children: ReactNode }) {
  const [client] = useState(() => new QueryClient({
    defaultOptions: { queries: { staleTime: 15_000, refetchOnWindowFocus: true, retry: (n, e) => !(e instanceof ApiError && e.status >= 400 && e.status < 500) && n < 2 } },
  }));
  return (
    <QueryClientProvider client={client}>
      <I18nProvider><ToastProvider><AuthProvider>{children}</AuthProvider></ToastProvider></I18nProvider>
    </QueryClientProvider>
  );
}
