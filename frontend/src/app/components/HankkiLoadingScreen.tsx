import { useEffect, useState } from 'react';
import loadingIcon from '@/assets/images/hankki-loading-icon.png';

export default function HankkiLoadingScreen({ label = '잠시만 기다려주세요' }: { label?: string }) {
  const [progress, setProgress] = useState(0);

  useEffect(() => {
    const startedAt = performance.now();
    const timer = window.setInterval(() => {
      const elapsed = performance.now() - startedAt;
      const nextProgress = Math.min(100, Math.round((elapsed / 560) * 100));
      setProgress(nextProgress);
    }, 24);

    return () => window.clearInterval(timer);
  }, []);

  return (
    <div className="flex min-h-screen items-center justify-center bg-[#fffaf4] px-6">
      <div className="flex w-full max-w-xs flex-col items-center text-center">
        <div className="relative flex h-44 w-44 items-center justify-center">
          <div className="absolute inset-0 rounded-full bg-[#f97316]/10 blur-2xl animate-pulse" />
          <svg className="absolute inset-0 h-full w-full -rotate-90" viewBox="0 0 160 160" aria-hidden="true">
            <defs>
              <linearGradient id="hankki-loading-gradient" x1="24" y1="24" x2="136" y2="136" gradientUnits="userSpaceOnUse">
                <stop offset="0%" stopColor="#f6c46f" />
                <stop offset="48%" stopColor="#e98f5f" />
                <stop offset="100%" stopColor="#c95735" />
              </linearGradient>
            </defs>
            <circle cx="80" cy="80" r="66" fill="none" stroke="#f3e4d6" strokeWidth="13" />
            <circle
              cx="80"
              cy="80"
              r="66"
              fill="none"
              stroke="url(#hankki-loading-gradient)"
              strokeWidth="13"
              strokeLinecap="round"
              pathLength="100"
              strokeDasharray={`${progress} 100`}
              className="transition-[stroke-dasharray] duration-75 ease-out"
            />
            <circle
              cx="80"
              cy="80"
              r="66"
              fill="none"
              stroke="url(#hankki-loading-gradient)"
              strokeWidth="13"
              strokeLinecap="round"
              pathLength="100"
              strokeDasharray="34 100"
              className="origin-center animate-[hankki-loading-tail_1.05s_cubic-bezier(0.65,0,0.35,1)_infinite] opacity-95"
            />
          </svg>
          <div className="relative flex h-32 w-32 items-center justify-center rounded-full bg-white shadow-[0_18px_45px_rgba(166,93,54,0.16)]">
            <img
              src={loadingIcon}
              alt="한끼팟 로딩"
              className="h-[118px] w-[118px] object-contain drop-shadow-[0_8px_18px_rgba(166,93,54,0.16)] animate-[hankki-loading-float_1.35s_ease-in-out_infinite]"
            />
          </div>
        </div>

        <div className="mt-6 flex flex-col items-center gap-1">
          <p className="text-sm font-bold text-[#6d4c41]">{label}</p>
          <div className="text-sm font-extrabold text-[#c95735]">
            {progress}%
          </div>
        </div>
      </div>
    </div>
  );
}
