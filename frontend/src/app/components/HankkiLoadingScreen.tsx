import { useEffect, useState } from 'react';
import loadingIcon from '@/assets/images/hankki-loading-icon.png';

export default function HankkiLoadingScreen({ label = '잠시만 기다려주세요' }: { label?: string }) {
  const [progress, setProgress] = useState(6);

  useEffect(() => {
    const timer = window.setInterval(() => {
      setProgress((current) => {
        if (current >= 96) return 96;

        return Math.min(96, current + Math.max(0.35, (96 - current) * 0.055));
      });
    }, 50);

    return () => window.clearInterval(timer);
  }, []);

  const displayProgress = Math.round(progress);
  const color =
    progress < 35
      ? '#f97316'
      : progress < 70
        ? '#22c55e'
        : '#d84315';

  return (
    <div className="flex min-h-screen items-center justify-center bg-[#fffaf4] px-6">
      <div className="flex w-full max-w-xs flex-col items-center text-center">
        <div
          className="relative flex h-32 w-32 items-center justify-center rounded-full shadow-[0_18px_45px_rgba(216,67,21,0.16)] transition-[background] duration-150"
          style={{
            background: `conic-gradient(${color} ${progress * 3.6}deg, #f1e4d7 0deg)`,
          }}
        >
          <div className="absolute inset-[-10px] rounded-full bg-[#f97316]/10 blur-xl animate-pulse" />
          <div className="relative flex h-[112px] w-[112px] items-center justify-center rounded-full bg-white">
            <img
              src={loadingIcon}
              alt="한끼팟 로딩"
              className="h-20 w-20 object-contain drop-shadow-[0_8px_18px_rgba(216,67,21,0.18)] animate-[hankki-loading-float_1.35s_ease-in-out_infinite]"
            />
          </div>
        </div>

        <div className="mt-6 w-full">
          <div className="mb-2 flex items-center justify-between text-xs font-bold text-[#8d6e63]">
            <span>{label}</span>
            <span style={{ color }}>{displayProgress}%</span>
          </div>
          <div className="h-2.5 overflow-hidden rounded-full bg-[#f1e4d7] shadow-inner">
            <div
              className="relative h-full overflow-hidden rounded-full transition-all duration-100 ease-out"
              style={{
                width: `${progress}%`,
                background: `linear-gradient(90deg, #fbbf24, ${color})`,
              }}
            >
              <span className="absolute inset-y-0 left-0 w-16 -translate-x-full bg-white/45 blur-[1px] animate-[hankki-loading-shine_1s_ease-in-out_infinite]" />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
