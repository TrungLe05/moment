"use client";

import { useEffect, useRef, useState } from "react";
import { useI18n } from "@/i18n/I18nProvider";
import { format, type Messages } from "@/i18n/messages";
import { CameraError, platform, type CameraErrorKind, type CameraFacing, type CapturedMedia } from "@/platform";
import { useCamera } from "./use-camera";

/** US-21, BR-08: video tối đa 5 giây. */
export const MAX_VIDEO_MS = 5000;
const TIMER_TICK_MS = 100;

type Mode = "photo" | "video";
type Recording = { stop: () => void; startedAt: number };

type Props = {
  facing: CameraFacing;
  onFlip: () => void;
  onCaptured: (media: CapturedMedia) => void;
};

function cameraErrorText(t: Messages, kind: CameraErrorKind): string {
  switch (kind) {
    case "denied":
      return t.compose.deniedBody;
    case "not-found":
      return t.compose.notFound;
    case "busy":
      return t.compose.busy;
    case "unsupported":
      return t.compose.unsupported;
    default:
      return t.compose.unknown;
  }
}

/** Khung ngắm và nút chụp (US-20) hoặc quay tối đa 5 giây có đồng hồ đếm (US-21). */
export function CameraCapture({ facing, onFlip, onCaptured }: Props) {
  const { t, locale } = useI18n();
  const { state, retry } = useCamera(facing);
  const viewfinder = useRef<HTMLVideoElement>(null);
  const [mode, setMode] = useState<Mode>("photo");
  const [recording, setRecording] = useState<Recording | null>(null);
  const [elapsedMs, setElapsedMs] = useState(0);
  const [failure, setFailure] = useState<string | null>(null);
  const stream = state.status === "ready" ? state.stream : null;

  useEffect(() => {
    if (viewfinder.current) viewfinder.current.srcObject = stream;
  }, [stream]);

  // Rời trang khi đang quay: dừng quay và bỏ kết quả, không gọi onCaptured của trang đã đóng.
  const mounted = useRef(true);
  const recordingRef = useRef<Recording | null>(null);
  useEffect(() => {
    recordingRef.current = recording;
  }, [recording]);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      recordingRef.current?.stop();
    };
  }, []);

  useEffect(() => {
    if (!recording) return;
    const timer = setInterval(
      () => setElapsedMs(Math.min(Date.now() - recording.startedAt, MAX_VIDEO_MS)),
      TIMER_TICK_MS,
    );
    return () => clearInterval(timer);
  }, [recording]);

  const takePhoto = async () => {
    if (!viewfinder.current) return;
    setFailure(null);
    try {
      onCaptured(await platform.camera.takePhoto(viewfinder.current));
    } catch {
      setFailure(t.compose.captureFailed);
    }
  };

  const startRecording = async () => {
    if (!stream) return;
    setFailure(null);
    try {
      const started = await platform.camera.startRecording(stream, MAX_VIDEO_MS);
      setElapsedMs(0);
      setRecording({ stop: started.stop, startedAt: Date.now() });
      started.result.then(
        (media) => {
          if (!mounted.current) return;
          setRecording(null);
          onCaptured(media);
        },
        () => {
          if (!mounted.current) return;
          setRecording(null);
          setFailure(t.compose.captureFailed);
        },
      );
    } catch (error: unknown) {
      const unsupported = error instanceof CameraError && error.kind === "unsupported";
      setFailure(unsupported ? t.compose.recordUnsupported : t.compose.captureFailed);
    }
  };

  const shutterLabel = mode === "photo" ? t.compose.takePhoto : recording ? t.compose.stopRecording : t.compose.startRecording;
  const onShutter = () => {
    if (mode === "photo") void takePhoto();
    else if (recording) recording.stop();
    else void startRecording();
  };
  const seconds = new Intl.NumberFormat(locale, { minimumFractionDigits: 1, maximumFractionDigits: 1 });

  return (
    <div className="flex flex-col gap-4">
      <div className="relative aspect-[358/410] w-full overflow-hidden rounded-[28px] bg-night-surface">
        {state.status === "error" ? (
          <div role="alert" className="flex h-full flex-col items-center justify-center gap-3 px-8 text-center">
            {state.kind === "denied" && <p className="text-base font-semibold">{t.compose.deniedTitle}</p>}
            <p className="text-sm text-night-muted">{cameraErrorText(t, state.kind)}</p>
            {state.kind !== "unsupported" && state.kind !== "not-found" && (
              <button
                type="button"
                onClick={retry}
                className="h-11 rounded-full bg-peach px-5 text-sm font-semibold text-peach-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-peach"
              >
                {t.compose.retry}
              </button>
            )}
          </div>
        ) : (
          <>
            {/* Camera trước lật ngang như soi gương; ảnh lưu lại vẫn đúng chiều camera thấy. */}
            <video
              ref={viewfinder}
              aria-label={t.compose.viewfinder}
              autoPlay
              muted
              playsInline
              className={`h-full w-full object-cover ${facing === "user" ? "-scale-x-100" : ""}`}
            />
            {state.status === "opening" && (
              <p role="status" className="absolute inset-0 flex items-center justify-center text-sm text-night-muted">
                {t.compose.opening}
              </p>
            )}
            {recording && (
              <p
                role="timer"
                className="absolute top-3.5 left-3.5 flex items-center gap-1.5 rounded-full bg-night/60 px-3 py-1.5 text-[13px] font-semibold text-white backdrop-blur-md"
              >
                <span aria-hidden="true" className="size-2 rounded-full bg-[#FF6B6B]" />
                {format(t.compose.recording, {
                  elapsed: seconds.format(Math.floor(elapsedMs / TIMER_TICK_MS) / 10),
                  limit: String(MAX_VIDEO_MS / 1000),
                })}
              </p>
            )}
          </>
        )}
      </div>

      {failure && (
        <p role="alert" className="text-center text-sm text-peach">
          {failure}
        </p>
      )}

      <div role="radiogroup" aria-label={t.compose.modes} className="mx-auto grid w-48 grid-cols-2 gap-1 rounded-full bg-night-surface p-1">
        {(["photo", "video"] as const).map((option) => (
          <label
            key={option}
            className={`flex h-9 cursor-pointer items-center justify-center rounded-full text-sm font-semibold has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-peach ${
              mode === option ? "bg-night-text text-night" : "text-night-muted"
            }`}
          >
            <input
              type="radio"
              name="capture-mode"
              value={option}
              checked={mode === option}
              disabled={recording !== null}
              onChange={() => setMode(option)}
              className="sr-only"
            />
            {t.compose[option]}
          </label>
        ))}
      </div>

      <div className="grid grid-cols-3 items-center">
        <span />
        <button
          type="button"
          aria-label={shutterLabel}
          disabled={state.status !== "ready"}
          onClick={onShutter}
          className="mx-auto flex size-[76px] items-center justify-center rounded-full border-4 border-night-text disabled:opacity-40 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-peach"
        >
          <span
            aria-hidden="true"
            className={
              recording
                ? "size-7 rounded-md bg-[#FF6B6B]"
                : `size-[60px] rounded-full ${mode === "video" ? "bg-[#FF6B6B]" : "bg-night-text"}`
            }
          />
        </button>
        <button
          type="button"
          aria-label={t.compose.flip}
          disabled={recording !== null}
          onClick={onFlip}
          className="ml-auto flex size-11 items-center justify-center rounded-full bg-night-surface disabled:opacity-40 focus-visible:outline-2 focus-visible:outline-peach"
        >
          <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M20 11a8 8 0 0 0-14.3-4.9L4 8" />
            <path d="M4 4v4h4" />
            <path d="M4 13a8 8 0 0 0 14.3 4.9L20 16" />
            <path d="M20 20v-4h-4" />
          </svg>
        </button>
      </div>
    </div>
  );
}
