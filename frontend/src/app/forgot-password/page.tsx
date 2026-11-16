import { PageGate } from "@/components/page-gate";
import { ForgotPasswordForm } from "@/features/auth/forgot-password-form";

export default function ForgotPasswordPage() {
  return (
    <PageGate requirement="signed-out">
      <ForgotPasswordForm />
    </PageGate>
  );
}
