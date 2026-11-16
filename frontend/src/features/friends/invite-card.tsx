"use client";

import { QRCodeSVG } from "qrcode.react";
import { useState } from "react";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { StatusMessage } from "@/components/auth-layout";
import { Button } from "@/components/ui/button";
import { errorText } from "@/i18n/error-text";
import { useI18n } from "@/i18n/I18nProvider";
import type { Invite } from "@/lib/api/friends";
import { useAsyncAction } from "@/lib/use-async-action";
import { platform } from "@/platform";

/** US-10, US-14: link mời, mã QR tạo ở client từ link, chia sẻ và tạo link mới. */
export function InviteCard({ invite, onRotate }: { invite: Invite; onRotate: () => Promise<void> }) {
  const { t } = useI18n();
  const [confirmOpen, setConfirmOpen] = useState(false);
  const share = useAsyncAction(() => platform.share.share({ title: t.appName, url: invite.url }));
  const rotate = useAsyncAction(onRotate);
  const [shareResult, setShareResult] = useState<string | null>(null);

  async function onShare() {
    const result = await share.run();
    setShareResult(result === "copied" ? t.friends.copied : null);
  }

  return (
    <section aria-labelledby="invite-title" className="flex flex-col gap-3">
      <h2 id="invite-title" className="text-lg font-semibold">
        {t.friends.inviteTitle}
      </h2>
      <p className="text-sm text-muted-foreground">{t.friends.inviteDescription}</p>
      <div className="flex justify-center rounded-lg bg-white p-4">
        <QRCodeSVG value={invite.url} size={180} title={t.friends.qrLabel} />
      </div>
      <p className="break-all rounded-md bg-muted px-3 py-2 text-sm">{invite.url}</p>
      <div className="flex flex-wrap gap-2">
        <Button type="button" onClick={() => void onShare()} disabled={share.pending}>
          {t.friends.share}
        </Button>
        <Button type="button" variant="outline" onClick={() => setConfirmOpen(true)} disabled={rotate.pending}>
          {t.friends.rotate}
        </Button>
      </div>
      {shareResult && <StatusMessage tone="success">{shareResult}</StatusMessage>}
      {(share.error ?? rotate.error) !== null && (
        <StatusMessage tone="error">{errorText(t, share.error ?? rotate.error)}</StatusMessage>
      )}
      <ConfirmDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title={t.friends.rotateTitle}
        description={t.friends.rotateBody}
        confirmLabel={t.friends.confirm}
        cancelLabel={t.friends.cancel}
        onConfirm={() => void rotate.run()}
      />
    </section>
  );
}
