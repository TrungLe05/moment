"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useEffect } from "react";
import { safeReturnPath, withReturnPath } from "@/lib/return-path";
import { useSession } from "./session";

type Requirement = "signed-out" | "onboarding" | "ready";

/**
 * Điều hướng theo trạng thái phiên (luồng màn hình, đặc tả mục 4):
 * - "signed-out": trang đăng nhập, đăng ký; đã đăng nhập thì về `next` (nếu có) hoặc trang chính.
 * - "onboarding": trang chọn username; chưa đăng nhập thì sang đăng nhập.
 * - "ready": trang cần hồ sơ hoàn tất; chưa có username thì sang onboarding (US-06).
 * Tham số `next` được chuyển tiếp qua các bước để cuối cùng quay về đúng trang (US-11).
 */
export function useSessionRedirect(requirement: Requirement): boolean {
  const router = useRouter();
  const next = safeReturnPath(useSearchParams().get("next"));
  const { status, user } = useSession();

  const target = (() => {
    if (status === "loading") return null;
    if (requirement === "signed-out") {
      if (status !== "signed-in") return null;
      if (user?.needsOnboarding) return next ? withReturnPath("/onboarding", next) : "/onboarding";
      return next ?? "/";
    }
    if (status === "signed-out") return next ? withReturnPath("/login", next) : "/login";
    if (requirement === "ready" && user?.needsOnboarding) return "/onboarding";
    if (requirement === "onboarding" && user && !user.needsOnboarding) return next ?? "/";
    return null;
  })();

  useEffect(() => {
    if (target) router.replace(target);
  }, [router, target]);

  return status !== "loading" && target === null;
}
