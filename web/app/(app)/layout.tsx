'use client';
import type { ReactNode } from 'react';
import { Shell } from '@/components/Shell';
import { ProjectProvider } from '@/lib/project';
import { RealtimeProvider } from '@/lib/realtime';

export default function AppLayout({ children }: { children: ReactNode }) {
  return <ProjectProvider><RealtimeProvider><Shell>{children}</Shell></RealtimeProvider></ProjectProvider>;
}
