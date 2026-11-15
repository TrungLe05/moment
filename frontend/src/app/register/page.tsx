import { PageGate } from "@/components/page-gate";
import { RegisterForm } from "@/features/auth/register-form";

export default function RegisterPage() {
  return (
    <PageGate requirement="signed-out">
      <RegisterForm />
    </PageGate>
  );
}
