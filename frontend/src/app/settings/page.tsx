import { PageGate } from "@/components/page-gate";
import { SettingsView } from "@/features/settings/settings-view";

export default function SettingsPage() {
  return (
    <PageGate requirement="ready">
      <SettingsView />
    </PageGate>
  );
}
