import { ApiError } from "@/lib/api/errors";
import type { Messages } from "./messages";

type ErrorKey = keyof Messages["errors"];
type FieldErrorKey = keyof Messages["fieldErrors"];

function isErrorKey(t: Messages, code: string): code is ErrorKey {
  return Object.hasOwn(t.errors, code);
}

function isFieldErrorKey(t: Messages, code: string): code is FieldErrorKey {
  return Object.hasOwn(t.fieldErrors, code);
}

/** Câu thông báo cho một lỗi, chọn theo `code` (không dùng title/detail của server). */
export function errorText(t: Messages, error: unknown): string {
  if (error instanceof ApiError && isErrorKey(t, error.code)) {
    return t.errors[error.code];
  }
  return t.errors.UNKNOWN;
}

/** Thông báo lỗi của một trường trong `errors`, hoặc undefined nếu trường đó không lỗi. */
export function fieldErrorText(t: Messages, error: unknown, field: string): string | undefined {
  if (!(error instanceof ApiError)) {
    return undefined;
  }
  const found = error.fieldErrors.find((item) => item.field === field);
  if (!found) {
    return undefined;
  }
  return isFieldErrorKey(t, found.code) ? t.fieldErrors[found.code] : t.fieldErrors.INVALID;
}
