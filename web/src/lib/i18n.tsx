'use client';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { messages, type Lang } from './messages';

const Ctx = createContext<{ lang: Lang; setLang: (l: Lang) => void; t: (k: string, vars?: Record<string, string | number>) => string; locale: string }>({
  lang: 'ru', setLang: () => {}, t: (k) => k, locale: 'ru-RU',
});
const LOCALES: Record<Lang, string> = { ru: 'ru-RU', kk: 'kk-KZ', en: 'en-GB' };

export function translate(lang: Lang, key: string, vars?: Record<string, string | number>): string {
  let s = messages[lang]?.[key] ?? messages.ru[key] ?? key;
  if (vars) for (const [k, v] of Object.entries(vars)) s = s.replace(`{${k}}`, String(v));
  return s;
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>('ru');
  useEffect(() => {
    try { const s = localStorage.getItem('jondeu.lang') as Lang | null; if (s && s in messages) setLangState(s); } catch { /* ignore */ }
  }, []);
  useEffect(() => { document.documentElement.lang = lang; }, [lang]);
  const setLang = useCallback((l: Lang) => { setLangState(l); try { localStorage.setItem('jondeu.lang', l); } catch { /* ignore */ } }, []);
  const t = useCallback((k: string, vars?: Record<string, string | number>) => translate(lang, k, vars), [lang]);
  const value = useMemo(() => ({ lang, setLang, t, locale: LOCALES[lang] }), [lang, setLang, t]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export const useI18n = () => useContext(Ctx);
export const useT = () => useContext(Ctx).t;
