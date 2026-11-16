import { PageGate } from "@/components/page-gate";
import { HomeView } from "@/features/home/home-view";

export default function HomePage() {
  return (
    <PageGate requirement="ready">
      <HomeView />
    </PageGate>
  );
}
