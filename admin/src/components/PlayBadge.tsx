/** Badge phát kiểu YouTube: tỉ lệ 1.42:1, bo góc 0.28h, tam giác trắng. */
export function PlayBadge({ height = 22 }: { height?: number }) {
  const w = height * 1.42
  return (
    <svg width={w} height={height} viewBox="0 0 142 100" aria-hidden>
      <rect width="142" height="100" rx="28" fill="var(--color-yt-red)" />
      <path d="M58 28 L58 72 L96 50 Z" fill="#fff" />
    </svg>
  )
}
