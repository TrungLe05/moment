// Lỗi API theo Problem Details (danh-sach-api.md mục 1). Client xử lý theo `code`, không theo `title`/`detail`.

export type FieldError = { field: string; code: string };

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly fieldErrors: FieldError[] = [],
    readonly retryAfterSeconds: number | null = null,
    /** Câu giải thích của server; chỉ để hiển thị, không dùng để rẽ nhánh xử lý. */
    readonly detail: string | null = null,
  ) {
    super(code);
    this.name = "ApiError";
  }
}

/** Mã riêng của client khi không gọi được server. */
export const NETWORK_ERROR = "NETWORK_ERROR";

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function toFieldErrors(value: unknown): FieldError[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.flatMap((item: unknown) =>
    isRecord(item) && typeof item.field === "string" && typeof item.code === "string"
      ? [{ field: item.field, code: item.code }]
      : [],
  );
}

export function apiErrorFromProblem(status: number, body: unknown, retryAfter: string | null): ApiError {
  const code = isRecord(body) && typeof body.code === "string" ? body.code : "INTERNAL_ERROR";
  const errors = isRecord(body) ? toFieldErrors(body.errors) : [];
  const retrySeconds = retryAfter !== null && /^\d+$/.test(retryAfter) ? Number(retryAfter) : null;
  const detail = isRecord(body) && typeof body.detail === "string" ? body.detail : null;
  return new ApiError(status, code, errors, retrySeconds, detail);
}
