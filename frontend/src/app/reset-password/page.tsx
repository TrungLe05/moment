import { Suspense } from "react";
import { ResetPasswordForm } from "@/features/auth/reset-password-form";

// Không chặn theo phiên: người dùng có thể mở link đặt lại mật khẩu khi vẫn còn phiên cũ.
export default function ResetPasswordPage() {
  return (
    <Suspense>
      <ResetPasswordForm />
    </Suspense>
  );
}
