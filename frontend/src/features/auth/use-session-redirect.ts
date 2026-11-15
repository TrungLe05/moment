"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { useSession } from "./session";

type Requirement = "signed-out" | "onboarding" | "ready";

/**
 * Điều hướng theo trạng thái phiên (luồng màn hình, đặc tả mục 4):
 * - "signed-out": trang đăng nhập, đăng ký; đã đăng nhập thì về trang chính.
 * - "onboarding": trang chọn username; chưa đăng nhập thì sang đăng nhập.
 * - "ready": trang cần hồ sơ hoàn tất; chưa có username thì sang onboarding (US-06).
 */
export function useSessionRedirect(requirement: Requirement): boolean {
  const router = useRouter();
  const { status, user } = useSession();

  const target = (() => {
    if (status === "loading") return null;
    if (requirement === "signed-out") return status === "signed-in" ? "/" : null;
    if (status === "signed-out") return "/login";
    if (requirement === "ready" && user?.needsOnboarding) return "/onboarding";
    if (requirement === "onboarding" && user && !user.needsOnboarding) return "/";
    return null;
  })();

  useEffect(() => {
    if (target) router.replace(target);
  }, [router, target]);

  return status !== "loading" && target === null;
}
