/** 바이트 수를 사람이 읽는 크기로. 첨부파일 목록에 "2.4MB"처럼 보여줄 때 쓴다. */
export function formatFileSize(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)}MB`;
  if (bytes >= 1024) return `${Math.round(bytes / 1024)}KB`;
  return `${bytes}B`;
}
