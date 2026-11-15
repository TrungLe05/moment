/** Đếm ký tự hiển thị theo grapheme (BR-11): emoji hay chữ có dấu tính là 1. */
export function countGraphemes(text: string): number {
  const segmenter = new Intl.Segmenter(undefined, { granularity: "grapheme" });
  let count = 0;
  for (const segment of segmenter.segment(text)) {
    void segment;
    count++;
  }
  return count;
}
