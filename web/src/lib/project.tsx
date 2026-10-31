'use client';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from './api';
import { useAuth } from './auth';
import type { Page, Project } from './types';

interface Ctx { projects: Project[]; project?: Project; projectId?: string; select: (id: string) => void; loading: boolean }
const C = createContext<Ctx>({ projects: [], select: () => {}, loading: true });

export function ProjectProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const [selected, setSelected] = useState<string | undefined>();
  useEffect(() => { try { setSelected(localStorage.getItem('jondeu.project') ?? undefined); } catch { /* ignore */ } }, []);
  const q = useQuery({ queryKey: ['projects'], queryFn: () => api.get<Page<Project>>('/api/v1/projects?size=100'), enabled: !!user });
  const projects = q.data?.content ?? [];
  const project = useMemo(() => projects.find((p) => p.id === selected) ?? projects.find((p) => p.status === 'IN_PROGRESS') ?? projects[0], [projects, selected]);
  const select = useCallback((id: string) => { setSelected(id); try { localStorage.setItem('jondeu.project', id); } catch { /* ignore */ } }, []);
  return <C.Provider value={{ projects, project, projectId: project?.id, select, loading: q.isLoading }}>{children}</C.Provider>;
}
export const useProject = () => useContext(C);
