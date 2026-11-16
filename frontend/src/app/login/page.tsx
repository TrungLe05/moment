import { PageGate } from "@/components/page-gate";
import { LoginForm } from "@/features/auth/login-form";

export default function LoginPage() {
  return (
    <PageGate requirement="signed-out">
      <LoginForm />
    </PageGate>
  );
}
