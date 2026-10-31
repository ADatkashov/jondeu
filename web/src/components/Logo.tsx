export function Logo({ light = false, size = 28 }: { light?: boolean; size?: number }) {
  return (
    <span className="inline-flex items-center gap-2 font-extrabold tracking-wide" style={{ fontSize: size * 0.7 }}>
      <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden>
        <rect width="32" height="32" rx="9" fill="#E99545" />
        <path d="M7 15.5 16 8l9 7.5V25a1 1 0 0 1-1 1h-5v-6h-6v6H8a1 1 0 0 1-1-1v-9.5Z" fill="#fff" />
      </svg>
      <span className={light ? 'text-white' : 'text-navy'}>JONDEU</span>
    </span>
  );
}
