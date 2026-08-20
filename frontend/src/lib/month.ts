// yyyy-MM 문자열 유틸. 백엔드 yearMonth 파라미터 형식과 일치시킨다.
export function currentYearMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

export function toYearMonth(d: Date | null): string {
  if (!d) return currentYearMonth();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

export function fromYearMonth(ym: string): Date {
  const [y, m] = ym.split('-').map(Number);
  return new Date(y, m - 1, 1);
}

export function formatMinutes(mins: number): string {
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  return m === 0 ? `${h}시간` : `${h}시간 ${m}분`;
}

// 백엔드는 출퇴근 시각을 기록의 workZone(직원 timezone) 오프셋이 붙은 ISO 문자열로 내려준다.
// Date로 파싱하면 브라우저 timezone으로 재해석되므로, 문자열에서 그대로 잘라 쓴다.
export function formatZonedTime(isoDateTime: string | null | undefined): string | null {
  if (!isoDateTime) return null;
  return isoDateTime.slice(11, 16);
}

export function zonedDatePart(isoDateTime: string | null | undefined): string | null {
  if (!isoDateTime) return null;
  return isoDateTime.slice(0, 10);
}
