import { Suspense } from "react";
import { InviteView } from "@/features/invite/invite-view";

// Không chặn theo phiên: người chưa đăng nhập cũng xem được ai mời mình (US-11).
export default function InvitePage() {
  return (
    <Suspense>
      <InviteView />
    </Suspense>
  );
}
