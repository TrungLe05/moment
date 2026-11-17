import { ApiError, NETWORK_ERROR } from "./errors";
import type { UploadTarget } from "./moments";

/** Mã riêng của client: object storage từ chối file (thường vì presigned URL đã hết hạn). */
export const UPLOAD_REJECTED = "UPLOAD_REJECTED";

/**
 * Tải một file thẳng lên object storage qua presigned URL (API mục 5), không đi qua backend.
 * Không gắn access token: URL đã mang chữ ký, gửi token cho storage là lộ token.
 */
export async function uploadFile(target: UploadTarget, file: Blob, fetchImpl: typeof fetch = fetch): Promise<void> {
  let response: Response;
  try {
    response = await fetchImpl(target.url, { method: target.method, headers: target.headers, body: file });
  } catch {
    throw new ApiError(0, NETWORK_ERROR);
  }
  if (!response.ok) {
    throw new ApiError(response.status, UPLOAD_REJECTED);
  }
}
