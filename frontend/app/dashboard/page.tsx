"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { HelpCircle, LogOut, Plus, Settings } from "lucide-react";
import { Button, Card, Input, Select } from "@/app/_components/ui";
import {
  WelcomeTour, OnboardingChecklist, EmptyKitState, useTourVisibility,
  type OnboardingState,
} from "./_Onboarding";
import { ServiceWorker } from "@/app/_components/ServiceWorker";
import { InstallPrompt } from "@/app/_components/InstallPrompt";
import { KitCard } from "./_KitCard";
import { BACKEND, authHeaders, errorMessage, get, post } from "./_lib/api";
import type { Feedback, Kit, Me, Tab } from "./_lib/types";
import { normalizeLocale, translator } from "@/app/_i18n";
import { dashboardDict } from "@/app/_i18n/dashboard";
import { rememberLocale } from "@/app/_i18n/useLocale";

const DEMO_EMAIL = "demo@localmediakit.app";
const emptyCreateForm = {
  title: "", headline: "", avatarUrl: "", theme: "light",
  accent: "violet", layout: "classic", language: "tr", slug: "",
};

/**
 * The dashboard shell.
 *
 * <p>It owns what is genuinely page-level — the session, the kit list, which
 * tab is open, and the single notice/error card — and nothing else. Each tab's
 * data and forms live in its own panel under _panels/, so this file does not
 * grow when a panel does.
 */
export default function DashboardPage() {
  const [me, setMe] = useState<Me | null>(null);
  const [kits, setKits] = useState<Kit[]>([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [form, setForm] = useState({ ...emptyCreateForm });
  const [active, setActive] = useState<{ kitId: number; tab: Tab } | null>(null);
  const [onboarding, setOnboarding] = useState<OnboardingState | null>(null);
  const [quickStartBusy, setQuickStartBusy] = useState(false);
  // Bumped on publish so an open Versions panel reloads without the shell
  // having to know how that panel fetches.
  const [versionsToken, setVersionsToken] = useState(0);

  /** The one place panels report to; keeps the notice card authoritative. */
  const feedback: Feedback = useMemo(
    () => ({
      notify: (message) => { setError(""); setNotice(message); },
      fail: (message) => { setNotice(""); setError(message); },
      clear: () => { setNotice(""); setError(""); },
    }),
    []
  );

  const loadKits = useCallback(async () => {
    const data = await get<Kit[]>("/api/mediakits");
    if (data) setKits(data);
  }, []);

  const loadOnboarding = useCallback(async () => {
    const data = await get<OnboardingState>("/api/me/onboarding");
    if (data) setOnboarding(data);
  }, []);

  // Creating, publishing and deleting all go through here, so the checklist
  // stays in step with the kit list without every handler having to remember.
  const refreshKitsAndProgress = useCallback(async () => {
    await Promise.all([loadKits(), loadOnboarding()]);
  }, [loadKits, loadOnboarding]);

  // The demo account intentionally never stores a dismissal server-side, so
  // each new visitor is introduced to the product. This keeps it from
  // reappearing on every reload for the visitor who already skipped it.
  const demoKey = me && me.email === DEMO_EMAIL ? "lmk.tour.demo" : null;
  const tour = useTourVisibility(onboarding, demoKey);

  useEffect(() => {
    const token = localStorage.getItem("token");
    if (!token) {
      setError(t("noSession"));
      return;
    }
    fetch(`${BACKEND}/api/me`, { headers: authHeaders() })
      .then((res) => (res.ok ? res.json() : Promise.reject(res.status)))
      .then((data: Me) => {
        setMe(data);
        return Promise.all([loadKits(), loadOnboarding()]);
      })
      .catch(() => setError(t("sessionExpired")));
    // Runs once at mount. `t` is deliberately not a dependency: at this point
    // there is no account to read a language from, so these two messages are
    // in the default language by definition — and listing it would refetch
    // /api/me every time the user changes their language.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadKits, loadOnboarding]);

  // The account locale drives the dashboard, and is mirrored into storage so
  // signing out (or visiting the landing page later) keeps the same language.
  const locale = normalizeLocale(me?.locale);
  const t = translator(dashboardDict, locale);
  useEffect(() => {
    if (me) rememberLocale(me.locale);
  }, [me]);

  // Account-level appearance preference, applied to the dashboard only. The
  // public media-kit page stamps its own per-kit theme on an inner scope, so a
  // visitor still sees the theme the kit's owner chose for it.
  useEffect(() => {
    if (!me) return;
    document.documentElement.setAttribute("data-theme", me.theme === "DARK" ? "dark" : "light");
    return () => document.documentElement.removeAttribute("data-theme");
  }, [me]);

  async function createKit(e: React.FormEvent) {
    e.preventDefault();
    feedback.clear();
    const result = await post<Kit>("/api/mediakits", form, t("failedCreate"), 201);
    if (result.ok) {
      setForm({ ...emptyCreateForm });
      await refreshKitsAndProgress();
    } else {
      feedback.fail(result.message);
    }
  }

  async function publishKit(id: number) {
    feedback.clear();
    const result = await post(`/api/mediakits/${id}/publish`, undefined, t("failedPublish"));
    if (result.ok) {
      feedback.notify(t("published"));
      await refreshKitsAndProgress();
      setVersionsToken((n) => n + 1);
    } else {
      feedback.fail(result.message);
    }
  }

  async function deleteKit(id: number) {
    feedback.clear();
    if (!window.confirm(t("confirmDeleteKit"))) return;
    const res = await fetch(`${BACKEND}/api/mediakits/${id}`, { method: "DELETE", headers: authHeaders() });
    if (res.status === 204) {
      if (active?.kitId === id) setActive(null);
      await refreshKitsAndProgress();
    } else {
      feedback.fail(await errorMessage(res, t("failedDelete")));
    }
  }

  /**
   * The suffix is added here rather than on the server: which word a creator
   * expects depends on the language they are reading, and that is known on this
   * side of the wire.
   */
  async function duplicateKit(id: number, title: string) {
    feedback.clear();
    const result = await post<Kit>(
      `/api/mediakits/${id}/duplicate`,
      { title: `${title} ${t("duplicateSuffix")}` },
      t("failedDuplicate"),
      201
    );
    if (result.ok) {
      await refreshKitsAndProgress();
      feedback.notify(t("duplicated"));
    } else {
      feedback.fail(result.message);
    }
  }

  async function openPreview(kitId: number) {
    feedback.clear();
    // Open the tab synchronously inside the click gesture — popup blockers
    // (notably Safari) reject window.open fired after an awaited fetch. The
    // target is our own same-origin /preview page, so no "noopener" needed
    // (and passing it would make window.open return null).
    const win = window.open("about:blank", "_blank");
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/preview-link`, {
      method: "POST",
      headers: authHeaders(),
    });
    if (res.ok) {
      const data = await res.json();
      const url = `/preview/${data.token}`;
      if (win) win.location.href = url;
      else window.location.href = url; // blocker still won: navigate in place
    } else {
      win?.close();
      feedback.fail(await errorMessage(res, t("failedPreview")));
    }
  }

  /** Clicking the open tab again collapses the card. */
  function selectTab(kitId: number, tab: Tab) {
    feedback.clear();
    setActive((prev) => (prev?.kitId === kitId && prev.tab === tab ? null : { kitId, tab }));
  }

  function updateKitField(id: number, field: keyof Kit, value: string | boolean) {
    setKits((prev) => prev.map((k) => (k.id === id ? { ...k, [field]: value } : k)));
  }

  function logout() {
    localStorage.removeItem("token");
    window.location.href = "/login";
  }

  /** Skipping is never blocked; the server records it, the UI hides it at once. */
  async function dismissOnboarding() {
    setOnboarding((prev) => (prev ? { ...prev, dismissed: true } : prev));
    if (demoKey) localStorage.setItem(demoKey, "1");
    await post("/api/me/onboarding/dismiss", undefined, t("failedDismiss"), 204);
    await loadOnboarding();
  }

  function focusCreateForm() {
    document.getElementById("create-kit-title")?.focus();
    document.getElementById("create-kit")?.scrollIntoView({ behavior: "smooth", block: "center" });
  }

  /**
   * Builds a first kit that already has something in it, so a new user can see
   * a real page and try Yayinla immediately instead of staring at empty
   * fields. Uses the ordinary endpoints — the content is a starting point to
   * edit, not seeded demo data.
   */
  async function quickStart() {
    feedback.clear();
    setQuickStartBusy(true);
    try {
      const created = await post<Kit>(
        "/api/mediakits",
        {
          title: me?.displayName || t("quickStartTitle"),
          headline: t("quickStartHeadline"),
          theme: "light",
        },
        "Olusturulamadi",
        201
      );
      if (!created.ok) {
        feedback.fail(created.message);
        return;
      }
      // Best-effort: a failed sample stat must not cost the user their kit.
      await post(
        `/api/mediakits/${created.data.id}/stats`,
        { platform: "YOUTUBE", followers: 1000, avgViews: 500, avgLikes: 50, avgComments: 10 },
        t("failedAddStat"),
        201
      ).catch(() => null);
      await refreshKitsAndProgress();
      setActive({ kitId: created.data.id, tab: "edit" });
      feedback.notify(t("quickStartDone"));
    } finally {
      setQuickStartBusy(false);
    }
  }

  if (!me) {
    return (
      <main className="grid min-h-screen place-items-center px-6 text-center">
        <div>
          <p className="text-muted">{error || t("loading")}</p>
          <Link href="/login" className="mt-3 inline-block font-medium text-brand hover:underline">
            {t("signIn")}
          </Link>
        </div>
      </main>
    );
  }

  return (
    <div className="min-h-screen">
      {/* Registered from the signed-in surfaces only — never from the public
          kit page, so a brand viewing a snapshot gets no worker at all. */}
      <ServiceWorker />
      {tour.open && <WelcomeTour onClose={tour.close} t={t} />}
      <header className="sticky top-0 z-10 border-b border-line bg-surface/80 backdrop-blur">
        <div className="mx-auto flex max-w-4xl items-center justify-between px-5 py-3">
          <Link href="/" className="flex items-center gap-2">
            <span className="grid h-7 w-7 place-items-center rounded-lg bg-brand-strong text-xs font-bold text-white">
              LM
            </span>
            <span className="font-semibold tracking-tight">LocalMediaKit</span>
          </Link>
          <div className="flex items-center gap-3">
            <span className="hidden text-sm text-muted sm:inline">{me.displayName}</span>
            <Button variant="ghost" size="sm" onClick={() => tour.setOpen(true)} title={t("headerTourTitle")}>
              <HelpCircle className="h-4 w-4" /> <span className="hidden sm:inline">{t("headerTour")}</span>
            </Button>
            <Link href="/dashboard/settings">
              <Button variant="ghost" size="sm"><Settings className="h-4 w-4" /> {t("headerSettings")}</Button>
            </Link>
            <Button variant="ghost" size="sm" onClick={logout}>
              <LogOut className="h-4 w-4" /> {t("headerSignOut")}
            </Button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-4xl px-5 py-8">
        {/* Notices / errors from any action below */}
        {(notice || error) && (
          <Card className="mb-6 p-4">
            {notice && <p className="rounded-lg bg-success/10 px-3 py-2 text-sm text-success">{notice}</p>}
            {error && <p className="rounded-lg bg-danger/10 px-3 py-2 text-sm text-danger">{error}</p>}
          </Card>
        )}

        {/* Getting started — hidden once dismissed, so it never nags. */}
        {onboarding && !onboarding.dismissed && (
          <OnboardingChecklist
            state={onboarding}
            t={t}
            onStartFirstKit={focusCreateForm}
            onDismiss={dismissOnboarding}
          />
        )}

        {/* Create kit */}
        <Card id="create-kit" className="mb-6 p-5">
          <h2 className="mb-3 font-semibold">{t("newKit")}</h2>
          <form onSubmit={createKit} className="grid gap-3 sm:grid-cols-2">
            <Input id="create-kit-title" placeholder={t("fieldTitleRequired")} value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })} required />
            <Input placeholder={t("fieldHeadline")} value={form.headline}
              onChange={(e) => setForm({ ...form, headline: e.target.value })} />
            <Input placeholder={t("fieldAvatarUrl")} value={form.avatarUrl}
              onChange={(e) => setForm({ ...form, avatarUrl: e.target.value })} />
            {/* The inputs around this one are named by their placeholder. A
                select has none, so without a label it reached a screen reader
                as an unnamed combo box -- "light theme, dark theme" with no
                indication of what is being chosen. Hidden rather than visible
                because this is a compact grid where every other field names
                itself in place; the settings page, which has room, uses a
                visible Label for the same control. */}
            <label htmlFor="create-kit-theme" className="sr-only">{t("fieldThemeLabel")}</label>
            <Select
              id="create-kit-theme"
              value={form.theme}
              onChange={(e) => setForm({ ...form, theme: e.target.value })}
            >
              <option value="light">{t("themeLightOption")}</option>
              <option value="dark">{t("themeDarkOption")}</option>
            </Select>
            <Input placeholder={t("fieldSlugOptional")} value={form.slug}
              onChange={(e) => setForm({ ...form, slug: e.target.value })} />
            <Button type="submit" className="sm:col-span-2 sm:justify-self-start">
              <Plus className="h-4 w-4" /> {t("create")}
            </Button>
          </form>
        </Card>

        <h2 className="mb-3 px-1 text-sm font-medium text-muted">{t("myKits", { count: kits.length })}</h2>
        {kits.length === 0 && (
          <EmptyKitState
            t={t}
            onStart={focusCreateForm}
            onQuickStart={quickStart}
            quickStartBusy={quickStartBusy}
          />
        )}
        <div className="grid gap-4">
          {kits.map((kit) => (
            <KitCard
              key={kit.id}
              kit={kit}
              t={t}
              locale={locale}
              openTab={active?.kitId === kit.id ? active.tab : null}
              versionsToken={versionsToken}
              feedback={feedback}
              onPreview={() => openPreview(kit.id)}
              onPublish={() => publishKit(kit.id)}
              onDelete={() => deleteKit(kit.id)}
              onDuplicate={() => duplicateKit(kit.id, kit.title)}
              onTabSelect={(tab) => selectTab(kit.id, tab)}
              onKitField={(field, value) => updateKitField(kit.id, field, value)}
              onKitSaved={loadKits}
              onProgressChanged={loadOnboarding}
            />
          ))}
        </div>

        {/* Only renders if the browser judged the app installable. */}
        <InstallPrompt locale={locale} />
      </main>
    </div>
  );
}
