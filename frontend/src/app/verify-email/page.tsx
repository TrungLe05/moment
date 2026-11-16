import { Suspense } from "react";
import { VerifyEmailView } from "@/features/auth/verify-email-view";

// Không chặn theo phiên: link trong email có thể mở trên máy đang đăng nhập tài khoản khác.
export default function VerifyEmailPage() {
  return (
    <Suspense>
      <VerifyEmailView />
    </Suspense>
  );
}
