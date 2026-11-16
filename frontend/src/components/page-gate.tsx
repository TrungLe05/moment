"use client";

import { Suspense, type ReactNode } from "react";
import { useSessionRedirect } from "@/features/auth/use-session-redirect";
import { useI18n } from "@/i18n/I18nProvider";

function Loading() {
  const { t } = useI18n();
  return (
    <p role="status" className="m-auto text-sm text-muted-foreground">
      {t.common.loading}
    </p>
  );
}

function Gate({ requirement, children }: { requirement: "signed-out" | "onboarding" | "ready"; children: ReactNode }) {
  const allowed = useSessionRedirect(requirement);
  return allowed ? children : <Loading />;
}

/**
 * Bọc mỗi trang: chờ khôi phục phiên, điều hướng nếu sai trạng thái, và có Suspense
 * cho useSearchParams khi xuất tĩnh.
 */
export function PageGate({ requirement, children }: { requirement: "signed-out" | "onboarding" | "ready"; children: ReactNode }) {
  return (
    <Suspense fallback={<Loading />}>
      <Gate requirement={requirement}>{children}</Gate>
    </Suspense>
  );
}
