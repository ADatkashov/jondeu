import type { Metadata, Viewport } from 'next';
import type { ReactNode } from 'react';
import { Providers } from '@/components/Providers';
import './globals.css';

export const metadata: Metadata = { title: 'JONDEU — Жөндеу оңай', description: 'Home renovation made simple. Управление ремонтом от заявки до сдачи объекта.' };
export const viewport: Viewport = { width: 'device-width', initialScale: 1, themeColor: '#183B50' };

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ru">
      <body className="min-h-screen font-sans"><Providers>{children}</Providers></body>
    </html>
  );
}
