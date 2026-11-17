import { Suspense } from "react";
import { PageGate } from "@/components/page-gate";
import { ArchiveView } from "@/features/archive/archive-view";

export default function ArchivePage() {
  return (
    <PageGate requirement="ready">
      <Suspense>
        <ArchiveView />
      </Suspense>
    </PageGate>
  );
}
