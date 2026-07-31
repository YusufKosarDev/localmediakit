"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  Check, ChevronLeft, ChevronRight, ExternalLink, Send, Sparkles, X,
} from "lucide-react";
import { Button, Card } from "@/app/_components/ui";
import type { Translate } from "./_lib/types";

export type OnboardingState = {
  dismissed: boolean;
  hasKit: boolean;
  hasStats: boolean;
  hasPublished: boolean;
  publicSlug: string | null;
};

/**
 * The one concept the product genuinely hides: editing a draft changes
 * nothing publicly until it is published. It leads the tour because every
 * other confusion downstream ("why isn't my change on the page?") traces
 * back to it.
 */

/**
 * Welcome tour. Hand-rolled rather than pulled from a tour library: the
 * dashboard's real surfaces (tabs, publish, analytics) do not exist on an
 * empty account, so there is nothing for a spotlight to point at — and a
 * positioning overlay would cost bundle size and break on small screens.
 */
export function WelcomeTour({ onClose, t }: { onClose: () => void; t: Translate }) {
  const SLIDES = [
    { title: t("tour1Title"), body: t("tour1Body") },
    { title: t("tour2Title"), accent: t("tour2Accent"), body: t("tour2Body") },
    { title: t("tour3Title"), body: t("tour3Body") },
    { title: t("tour4Title"), body: t("tour4Body") },
  ] as { title: string; body: string; accent?: string }[];
  const [step, setStep] = useState(0);
  const closeRef = useRef<HTMLButtonElement>(null);
  const last = step === SLIDES.length - 1;

  // Escape closes it — skipping must always be one keystroke away.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    closeRef.current?.focus();
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  const slide = SLIDES[step];

  return (
    <div
      className="fixed inset-0 z-50 grid place-items-end bg-black/40 p-0 backdrop-blur-sm sm:place-items-center sm:p-6"
      role="dialog"
      aria-modal="true"
      aria-labelledby="tour-title"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <Card className="w-full max-w-lg rounded-b-none p-6 sm:rounded-2xl">
        <div className="flex items-start justify-between gap-4">
          <span className="inline-flex items-center gap-2 rounded-full bg-brand-weak px-2.5 py-0.5 text-xs font-medium text-brand">
            <Sparkles className="h-3.5 w-3.5" /> {step + 1} / {SLIDES.length}
          </span>
          <button
            ref={closeRef}
            onClick={onClose}
            aria-label={t("tourClose")}
            className="rounded-lg p-1 text-muted transition-colors hover:bg-page hover:text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand/50"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <h2 id="tour-title" className="mt-4 text-xl font-semibold tracking-tight">
          {slide.title}
        </h2>
        {slide.accent && (
          <p className="mt-3 rounded-xl border border-brand/20 bg-brand-weak px-3 py-2 text-sm font-medium text-brand">
            {slide.accent}
          </p>
        )}
        <p className="mt-3 text-sm leading-relaxed text-muted">{slide.body}</p>

        <div className="mt-6 flex items-center justify-between gap-3">
          <button
            onClick={onClose}
            className="text-sm text-muted underline-offset-4 hover:text-fg hover:underline"
          >
            {t("tourSkip")}
          </button>
          <div className="flex items-center gap-2">
            {step > 0 && (
              <Button variant="secondary" size="sm" onClick={() => setStep(step - 1)}>
                <ChevronLeft className="h-4 w-4" /> {t("tourBack")}
              </Button>
            )}
            <Button size="sm" onClick={() => (last ? onClose() : setStep(step + 1))}>
              {last ? t("tourStart") : t("tourNext")}
              {!last && <ChevronRight className="h-4 w-4" />}
            </Button>
          </div>
        </div>
      </Card>
    </div>
  );
}

/**
 * Progress list. Each step reflects what the account actually contains, so it
 * cannot claim something is done after the underlying kit or publish is gone.
 */
export function OnboardingChecklist({
  state,
  t,
  onStartFirstKit,
  onDismiss,
}: {
  state: OnboardingState;
  t: Translate;
  onStartFirstKit: () => void;
  onDismiss: () => void;
}) {
  const steps = [
    {
      done: state.hasKit,
      title: t("step1Title"),
      hint: t("step1Hint"),
    },
    {
      done: state.hasStats,
      title: t("step2Title"),
      hint: t("step2Hint"),
    },
    {
      done: state.hasPublished,
      title: t("step3Title"),
      hint: t("step3Hint"),
    },
  ];
  const doneCount = steps.filter((s) => s.done).length;
  const complete = doneCount === steps.length;

  return (
    <Card className="mb-6 p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="font-semibold">
            {complete ? t("checklistReady") : t("checklistTitle")}
          </h2>
          <p className="mt-0.5 text-sm text-muted">
            {complete
              ? t("checklistDone")
              : t("checklistProgress", { done: doneCount, total: steps.length })}
          </p>
        </div>
        <Button variant="ghost" size="sm" onClick={onDismiss}>
          {complete ? t("checklistClose") : t("checklistHide")}
        </Button>
      </div>

      {/* Bar doubles as the progress indicator on narrow screens. */}
      <div
        className="mt-4 flex gap-1"
        role="progressbar"
        aria-valuenow={doneCount}
        aria-valuemin={0}
        aria-valuemax={steps.length}
        aria-label={t("checklistProgressLabel")}
      >
        {steps.map((s, i) => (
          <span
            key={i}
            className={`h-1.5 flex-1 rounded-full ${s.done ? "bg-brand-strong" : "bg-line"}`}
          />
        ))}
      </div>

      <ol className="mt-4 grid gap-3">
        {steps.map((s, i) => (
          <li key={i} className="flex gap-3">
            <span
              aria-hidden="true"
              className={`mt-0.5 grid h-5 w-5 shrink-0 place-items-center rounded-full text-xs font-semibold ${
                s.done
                  ? "bg-success/15 text-success"
                  : "border border-line text-faint"
              }`}
            >
              {s.done ? <Check className="h-3 w-3" /> : i + 1}
            </span>
            <div className="min-w-0">
              <p className={`text-sm font-medium ${s.done ? "text-muted line-through" : "text-fg"}`}>
                {s.title}
                <span className="sr-only">{s.done ? t("checklistCompleted") : ""}</span>
              </p>
              {!s.done && <p className="mt-0.5 text-xs text-muted">{s.hint}</p>}
            </div>
          </li>
        ))}
      </ol>

      {!state.hasKit && (
        <Button className="mt-4" size="sm" onClick={onStartFirstKit}>
          <Send className="h-3.5 w-3.5" /> {t("createFirstKit")}
        </Button>
      )}
      {complete && state.publicSlug && (
        <a
          href={`/${state.publicSlug}`}
          target="_blank"
          rel="noreferrer"
          className="mt-4 inline-flex items-center gap-1.5 text-sm font-medium text-brand hover:underline"
        >
          {t("seePublicLink", { slug: state.publicSlug })} <ExternalLink className="h-3.5 w-3.5" />
        </a>
      )}
    </Card>
  );
}

/**
 * Shown in place of the bare kit list on a fresh account, so the first screen
 * says what a media kit is instead of showing an empty heading.
 */
export function EmptyKitState({
  t,
  onStart,
  onQuickStart,
  quickStartBusy,
}: {
  t: Translate;
  onStart: () => void;
  onQuickStart: () => void;
  quickStartBusy: boolean;
}) {
  return (
    <Card className="grid place-items-center px-6 py-12 text-center">
      <span className="grid h-12 w-12 place-items-center rounded-2xl bg-brand-weak text-brand">
        <Sparkles className="h-6 w-6" />
      </span>
      <h3 className="mt-4 font-semibold">{t("emptyTitle")}</h3>
      <p className="mt-1.5 max-w-md text-sm leading-relaxed text-muted">{t("emptyBody")}</p>
      <div className="mt-5 flex flex-wrap items-center justify-center gap-3">
        <Button size="sm" onClick={onStart}>
          {t("emptyStart")}
        </Button>
        <Button size="sm" variant="secondary" onClick={onQuickStart} disabled={quickStartBusy}>
          {quickStartBusy ? t("emptyPreparing") : t("emptyQuickStart")}
        </Button>
      </div>
    </Card>
  );
}

/** Re-opens the tour from the header; onboarding is never a one-shot. */
export function useTourVisibility(state: OnboardingState | null, demoKey: string | null) {
  const [open, setOpen] = useState(false);
  const decided = useRef(false);

  useEffect(() => {
    if (!state || decided.current) return;
    decided.current = true;
    // Only interrupt a genuinely new account: someone who already built a kit
    // gets the checklist instead of a modal in their face.
    if (state.dismissed || state.hasKit) return;
    // The demo account never persists its dismissal server-side, so a per
    // browser flag keeps it from reappearing on every reload for one visitor.
    if (demoKey && localStorage.getItem(demoKey)) return;
    setOpen(true);
  }, [state, demoKey]);

  const close = useCallback(() => {
    setOpen(false);
    if (demoKey) localStorage.setItem(demoKey, "1");
  }, [demoKey]);

  return { open, setOpen, close };
}
