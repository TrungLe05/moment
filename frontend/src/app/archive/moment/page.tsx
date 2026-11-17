import { Suspense } from "react";
import { PageGate } from "@/components/page-gate";
import { ArchiveMomentView } from "@/features/archive/archive-moment-view";

// Xuất tĩnh không có route động cho từng id: id nằm trong query (?id=), như trang mời (D-032).
export default function ArchiveMomentPage() {
  return (
    <PageGate requirement="ready">
      <Suspense>
        <ArchiveMomentView />
      </Suspense>
    </PageGate>
  );
}
