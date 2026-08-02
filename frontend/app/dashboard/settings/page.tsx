"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { ArrowLeft, Download, Trash2, ShieldAlert } from "lucide-react";
import { Button, Card, Input, Label, Select } from "@/app/_components/ui";
import { normalizeLocale, translator } from "@/app/_i18n";
import { dashboardDict } from "@/app/_i18n/dashboard";
import { rememberLocale } from "@/app/_i18n/useLocale";

const BACKEND = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";

// The exact phrase the backend requires before it will delete an account.
const DELETE_CONFIRMATION = "HESABIMI SIL";

type Me = {
  id: number;
  email: string;
  displayName: string;
  avatarUrl: string | null;
  theme: string;
  plan: string;
  leadNotificationsEnabled: boolean;
  locale: string;
};

function authHeaders(): HeadersInit {
  const token = typeof window !== "undefined" ? localStorage.getItem("token") : null;
  return { "Content-Type": "application/json", Authorization: `Bearer ${token ?? ""}` };
}

/** Backend errors arrive as {status, error}; fall back to a generic line. */
async function errorText(res: Response, fallback: string): Promise<string> {
  try {
    const body = await res.json();
    if (typeof body?.error === "string" && body.error) return body.error;
  } catch {
    /* empty or non-JSON body */
  }
  return fallback;
}

export default function SettingsPage() {
  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);

  const [profile, setProfile] = useState({
    displayName: "", avatarUrl: "", theme: "LIGHT", leadNotificationsEnabled: true, locale: "tr",
  });
  const [profileMsg, setProfileMsg] = useState({ ok: "", err: "" });
  const [profileBusy, setProfileBusy] = useState(false);

  const [pw, setPw] = useState({ currentPassword: "", newPassword: "", repeat: "" });
  const [pwMsg, setPwMsg] = useState({ ok: "", err: "" });
  const [pwBusy, setPwBusy] = useState(false);

  const [mail, setMail] = useState({ currentPassword: "", newEmail: "" });
  const [mailMsg, setMailMsg] = useState({ ok: "", err: "" });
  const [mailBusy, setMailBusy] = useState(false);

  const [del, setDel] = useState({ currentPassword: "", confirmation: "" });
  const [delErr, setDelErr] = useState("");
  const [delBusy, setDelBusy] = useState(false);
  const [exportErr, setExportErr] = useState("");

  // Driven by the saved profile so the page switches language the moment the
  // picker below is saved, without a reload.
  const locale = normalizeLocale(profile.locale);
  const t = translator(dashboardDict, locale);

  const applyMe = useCallback((data: Me) => {
    setMe(data);
    setProfile({
      displayName: data.displayName,
      avatarUrl: data.avatarUrl ?? "",
      theme: data.theme,
      leadNotificationsEnabled: data.leadNotificationsEnabled,
      locale: data.locale,
    });
  }, []);

  useEffect(() => {
    const token = localStorage.getItem("token");
    if (!token) {
      window.location.href = "/login";
      return;
    }
    fetch(`${BACKEND}/api/me`, { headers: authHeaders() })
      .then(async (res) => {
        if (!res.ok) {
          window.location.href = "/login";
          return;
        }
        applyMe(await res.json());
      })
      .catch(() => setProfileMsg({ ok: "", err: t("unreachable") }))
      .finally(() => setLoading(false));
    // Runs once at mount. `t` is deliberately not listed: the profile has not
    // loaded yet, so this message is in the default language by definition, and
    // depending on it would refetch the profile whenever the language changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [applyMe]);

  // The saved theme drives the dashboard only. The public media-kit page keeps
  // stamping its own per-kit theme, so a visitor's view never depends on this.
  useEffect(() => {
    if (me) rememberLocale(me.locale);
  }, [me]);

  useEffect(() => {
    if (!me) return;
    document.documentElement.setAttribute("data-theme", me.theme === "DARK" ? "dark" : "light");
    return () => document.documentElement.removeAttribute("data-theme");
  }, [me]);

  async function saveProfile(e: React.FormEvent) {
    e.preventDefault();
    setProfileMsg({ ok: "", err: "" });
    setProfileBusy(true);
    try {
      const res = await fetch(`${BACKEND}/api/me`, {
        method: "PUT",
        headers: authHeaders(),
        body: JSON.stringify(profile),
      });
      if (!res.ok) {
        setProfileMsg({ ok: "", err: await errorText(res, t("profileFailed")) });
        return;
      }
      applyMe(await res.json());
      setProfileMsg({ ok: t("profileSaved"), err: "" });
    } catch {
      setProfileMsg({ ok: "", err: t("unreachable") });
    } finally {
      setProfileBusy(false);
    }
  }

  async function changePassword(e: React.FormEvent) {
    e.preventDefault();
    setPwMsg({ ok: "", err: "" });
    // Caught here so a typo never costs a round trip.
    if (pw.newPassword !== pw.repeat) {
      setPwMsg({ ok: "", err: t("passwordMismatch") });
      return;
    }
    if (pw.newPassword.length < 8) {
      setPwMsg({ ok: "", err: t("passwordTooShort") });
      return;
    }
    setPwBusy(true);
    try {
      const res = await fetch(`${BACKEND}/api/me/password`, {
        method: "POST",
        headers: authHeaders(),
        body: JSON.stringify({
          currentPassword: pw.currentPassword,
          newPassword: pw.newPassword,
        }),
      });
      if (!res.ok) {
        setPwMsg({ ok: "", err: await errorText(res, t("passwordFailed")) });
        return;
      }
      setPw({ currentPassword: "", newPassword: "", repeat: "" });
      setPwMsg({ ok: t("passwordChanged"), err: "" });
    } catch {
      setPwMsg({ ok: "", err: t("unreachable") });
    } finally {
      setPwBusy(false);
    }
  }

  async function changeEmail(e: React.FormEvent) {
    e.preventDefault();
    setMailMsg({ ok: "", err: "" });
    setMailBusy(true);
    try {
      const res = await fetch(`${BACKEND}/api/me/email`, {
        method: "POST",
        headers: authHeaders(),
        body: JSON.stringify(mail),
      });
      if (!res.ok) {
        setMailMsg({ ok: "", err: await errorText(res, t("emailFailed")) });
        return;
      }
      // The token that authenticated this call names the OLD address and is
      // now dead — swapping in the replacement keeps the session alive.
      const data = await res.json();
      localStorage.setItem("token", data.token);
      applyMe(data.user);
      setMail({ currentPassword: "", newEmail: "" });
      setMailMsg({ ok: t("emailChanged"), err: "" });
    } catch {
      setMailMsg({ ok: "", err: t("unreachable") });
    } finally {
      setMailBusy(false);
    }
  }

  /**
   * Fetched rather than linked: the endpoint needs the bearer token, which an
   * anchor cannot send.
   */
  async function downloadExport() {
    setExportErr("");
    try {
      const res = await fetch(`${BACKEND}/api/me/export`, { headers: authHeaders() });
      if (!res.ok) {
        setExportErr(await errorText(res, t("failedAccountExport")));
        return;
      }
      const url = URL.createObjectURL(await res.blob());
      const link = document.createElement("a");
      link.href = url;
      link.download = "localmediakit-export.json";
      link.click();
      URL.revokeObjectURL(url);
    } catch {
      setExportErr(t("failedAccountExport"));
    }
  }

  async function deleteAccount(e: React.FormEvent) {
    e.preventDefault();
    setDelErr("");
    setDelBusy(true);
    try {
      const res = await fetch(`${BACKEND}/api/me`, {
        method: "DELETE",
        headers: authHeaders(),
        body: JSON.stringify(del),
      });
      if (!res.ok) {
        setDelErr(await errorText(res, t("deleteFailed")));
        return;
      }
      localStorage.removeItem("token");
      window.location.href = "/";
    } catch {
      setDelErr(t("unreachable"));
    } finally {
      setDelBusy(false);
    }
  }

  if (loading) {
    return (
      <main className="mx-auto max-w-2xl px-5 py-16">
        <div className="h-32 animate-pulse rounded-2xl bg-page" />
      </main>
    );
  }
  if (!me) return null;

  const deleteArmed =
    del.confirmation.trim() === DELETE_CONFIRMATION && del.currentPassword.length > 0;

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-10 border-b border-line bg-surface/80 backdrop-blur">
        <div className="mx-auto flex max-w-2xl items-center justify-between px-5 py-3">
          <Link href="/dashboard" className="flex items-center gap-2 text-sm text-muted hover:text-fg">
            <ArrowLeft className="h-4 w-4" /> {t("backToDashboard")}
          </Link>
          <span className="text-sm text-muted">{me.email}</span>
        </div>
      </header>

      <main className="mx-auto max-w-2xl px-5 py-8">
        <h1 className="text-2xl font-semibold tracking-tight">{t("settingsTitle")}</h1>
        <p className="mt-1 text-sm text-muted">
          {t("settingsSubtitle")}
        </p>

        {/* ---------- Profile ---------- */}
        <Card className="mt-8 p-6">
          <h2 className="font-semibold">{t("profileTitle")}</h2>
          <p className="mt-1 text-sm text-muted">
            {t("profileSubtitle")}
          </p>

          <form onSubmit={saveProfile} className="mt-5 grid gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="displayName">{t("displayName")}</Label>
              <Input
                id="displayName"
                value={profile.displayName}
                onChange={(e) => setProfile({ ...profile, displayName: e.target.value })}
                maxLength={100}
                required
              />
            </div>

            <div className="grid gap-1.5">
              <Label htmlFor="avatarUrl">{t("fieldAvatarUrl")}</Label>
              <div className="flex items-center gap-3">
                {profile.avatarUrl ? (
                  // Remote, user-supplied host: a plain <img> avoids routing an
                  // arbitrary origin through the image optimizer.
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={profile.avatarUrl}
                    alt=""
                    className="h-10 w-10 shrink-0 rounded-full border border-line object-cover"
                  />
                ) : (
                  <span className="grid h-10 w-10 shrink-0 place-items-center rounded-full border border-line bg-page text-sm font-medium text-muted">
                    {me.displayName.slice(0, 1).toUpperCase()}
                  </span>
                )}
                <Input
                  id="avatarUrl"
                  type="url"
                  placeholder="https://..."
                  value={profile.avatarUrl}
                  onChange={(e) => setProfile({ ...profile, avatarUrl: e.target.value })}
                  maxLength={1000}
                />
              </div>
              <p className="text-xs text-faint">
                {t("avatarUrlHint")}
              </p>
            </div>

            <div className="grid gap-1.5">
              <Label htmlFor="theme">{t("dashboardTheme")}</Label>
              <Select
                id="theme"
                value={profile.theme}
                onChange={(e) => setProfile({ ...profile, theme: e.target.value })}
                className="w-full"
              >
                <option value="LIGHT">{t("themeLight")}</option>
                <option value="DARK">{t("themeDark")}</option>
              </Select>
              <p className="text-xs text-faint">
                {t("dashboardThemeHint")}
              </p>
            </div>

            <div className="flex items-center gap-3">
              <Button type="submit" disabled={profileBusy}>
                {profileBusy ? t("busy") : t("save")}
              </Button>
              {profileMsg.ok && <span className="text-sm text-success">{profileMsg.ok}</span>}
              {profileMsg.err && <span className="text-sm text-danger">{profileMsg.err}</span>}
            </div>
          </form>
        </Card>

        {/* ---------- Language ---------- */}
        <Card className="mt-6 p-6">
          <h2 className="font-semibold">{t("languageTitle")}</h2>
          <p className="mt-1 text-sm text-muted">
            {t("languageSubtitle")}
          </p>
          <div className="mt-5 grid gap-1.5">
            <Label htmlFor="locale">{t("interfaceLanguage")}</Label>
            <Select
              id="locale"
              value={profile.locale}
              onChange={(e) => setProfile({ ...profile, locale: e.target.value })}
              className="w-full"
            >
              <option value="tr">Turkce</option>
              <option value="en">English</option>
            </Select>
            <p className="text-xs text-faint">
              {t("kitLanguageNote")}
            </p>
          </div>
          <div className="mt-4">
            <Button type="button" onClick={saveProfile} disabled={profileBusy}>
              {profileBusy ? t("busy") : t("saveLanguage")}
            </Button>
          </div>
        </Card>

        {/* ---------- Notifications ---------- */}
        <Card className="mt-6 p-6">
          <h2 className="font-semibold">{t("notificationsTitle")}</h2>
          <p className="mt-1 text-sm text-muted">
            {t("notificationsSubtitle")}
          </p>

          <label
            htmlFor="leadNotifications"
            className="mt-5 flex cursor-pointer items-start gap-3 rounded-xl border border-line p-3"
          >
            <input
              id="leadNotifications"
              type="checkbox"
              checked={profile.leadNotificationsEnabled}
              onChange={(e) =>
                setProfile({ ...profile, leadNotificationsEnabled: e.target.checked })
              }
              className="mt-0.5 h-4 w-4 shrink-0 accent-[var(--brand-strong)]"
            />
            <span>
              <span className="text-sm font-medium">{t("leadEmailToggle")}</span>
              <span className="mt-0.5 block text-xs text-muted">
                {t("leadEmailHint")}
              </span>
            </span>
          </label>

          <div className="mt-4 flex items-center gap-3">
            <Button type="button" onClick={saveProfile} disabled={profileBusy}>
              {profileBusy ? t("busy") : t("saveNotifications")}
            </Button>
            <span className="text-xs text-faint">
              {t("thirdPartyNote")}
            </span>
          </div>
        </Card>

        {/* ---------- Password ---------- */}
        <Card className="mt-6 p-6">
          <h2 className="font-semibold">{t("passwordTitle")}</h2>
          <p className="mt-1 text-sm text-muted">{t("passwordSubtitle")}</p>

          <form onSubmit={changePassword} className="mt-5 grid gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="currentPassword">{t("currentPassword")}</Label>
              <Input
                id="currentPassword"
                type="password"
                autoComplete="current-password"
                value={pw.currentPassword}
                onChange={(e) => setPw({ ...pw, currentPassword: e.target.value })}
                required
              />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="newPassword">{t("newPassword")}</Label>
              <Input
                id="newPassword"
                type="password"
                autoComplete="new-password"
                value={pw.newPassword}
                onChange={(e) => setPw({ ...pw, newPassword: e.target.value })}
                minLength={8}
                required
              />
              <p className="text-xs text-faint">{t("passwordMinHint")}</p>
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="repeatPassword">{t("newPasswordAgain")}</Label>
              <Input
                id="repeatPassword"
                type="password"
                autoComplete="new-password"
                value={pw.repeat}
                onChange={(e) => setPw({ ...pw, repeat: e.target.value })}
                required
              />
            </div>
            <div className="flex items-center gap-3">
              <Button type="submit" disabled={pwBusy}>
                {pwBusy ? t("busy") : t("changePassword")}
              </Button>
              {pwMsg.ok && <span className="text-sm text-success">{pwMsg.ok}</span>}
              {pwMsg.err && <span className="text-sm text-danger">{pwMsg.err}</span>}
            </div>
          </form>
        </Card>

        {/* ---------- Email ---------- */}
        <Card className="mt-6 p-6">
          <h2 className="font-semibold">{t("emailTitle")}</h2>
          <p className="mt-1 text-sm text-muted">
            {t("emailCurrent")} <span className="text-fg">{me.email}</span>
          </p>

          <form onSubmit={changeEmail} className="mt-5 grid gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="newEmail">{t("newEmail")}</Label>
              <Input
                id="newEmail"
                type="email"
                value={mail.newEmail}
                onChange={(e) => setMail({ ...mail, newEmail: e.target.value })}
                required
              />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="emailPassword">{t("currentPassword")}</Label>
              <Input
                id="emailPassword"
                type="password"
                autoComplete="current-password"
                value={mail.currentPassword}
                onChange={(e) => setMail({ ...mail, currentPassword: e.target.value })}
                required
              />
            </div>
            <p className="rounded-lg bg-page px-3 py-2 text-xs text-muted">
              {t("emailVerifyNote")}
            </p>
            <div className="flex items-center gap-3">
              <Button type="submit" disabled={mailBusy}>
                {mailBusy ? t("busy") : t("changeEmail")}
              </Button>
              {mailMsg.ok && <span className="text-sm text-success">{mailMsg.ok}</span>}
              {mailMsg.err && <span className="text-sm text-danger">{mailMsg.err}</span>}
            </div>
          </form>
        </Card>

        {/* ---------- Data export ---------- */}
        <Card className="mt-6 p-6">
          <h2 className="flex items-center gap-2 font-semibold">
            <Download className="h-4 w-4" /> {t("accountExportTitle")}
          </h2>
          <p className="mt-1 text-sm text-muted">{t("accountExportHint")}</p>
          <div className="mt-4 flex items-center gap-3">
            <Button type="button" variant="secondary" onClick={downloadExport}>
              {t("accountExportButton")}
            </Button>
            {exportErr && <span className="text-sm text-danger">{exportErr}</span>}
          </div>
        </Card>

        {/* ---------- Danger zone ---------- */}
        <Card className="mt-6 border-danger/30 p-6">
          <h2 className="flex items-center gap-2 font-semibold text-danger">
            <ShieldAlert className="h-4 w-4" /> {t("dangerTitle")}
          </h2>
          <p className="mt-1 text-sm text-muted">
            {t("dangerBody1")} <span className="text-fg">{t("dangerBody2")}</span>{" "}
            {t("dangerBody3")}
          </p>

          <form onSubmit={deleteAccount} className="mt-5 grid gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="deletePassword">{t("currentPassword")}</Label>
              <Input
                id="deletePassword"
                type="password"
                autoComplete="current-password"
                value={del.currentPassword}
                onChange={(e) => setDel({ ...del, currentPassword: e.target.value })}
                required
              />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="deleteConfirmation">
                {t("deleteConfirmLabel", { phrase: DELETE_CONFIRMATION })}
              </Label>
              <Input
                id="deleteConfirmation"
                value={del.confirmation}
                onChange={(e) => setDel({ ...del, confirmation: e.target.value })}
                required
              />
            </div>
            <div className="flex items-center gap-3">
              <Button type="submit" variant="danger" disabled={!deleteArmed || delBusy}>
                <Trash2 className="h-4 w-4" />
                {delBusy ? t("busy") : t("deleteAccount")}
              </Button>
              {delErr && <span className="text-sm text-danger">{delErr}</span>}
            </div>
          </form>
        </Card>
      </main>
    </div>
  );
}
