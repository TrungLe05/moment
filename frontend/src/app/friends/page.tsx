import { PageGate } from "@/components/page-gate";
import { FriendsView } from "@/features/friends/friends-view";

export default function FriendsPage() {
  return (
    <PageGate requirement="ready">
      <FriendsView />
    </PageGate>
  );
}
