import { PageGate } from "@/components/page-gate";
import { OnboardingForm } from "@/features/onboarding/onboarding-form";

export default function OnboardingPage() {
  return (
    <PageGate requirement="onboarding">
      <OnboardingForm />
    </PageGate>
  );
}
