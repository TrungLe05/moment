import { PageGate } from "@/components/page-gate";
import { ComposeView } from "@/features/compose/compose-view";

export default function ComposePage() {
  return (
    <PageGate requirement="ready">
      <ComposeView />
    </PageGate>
  );
}
