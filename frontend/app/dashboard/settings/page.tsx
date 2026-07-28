"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { ArrowLeft, Trash2, ShieldAlert } from "lucide-react";
import { Button, Card, Input, Label, Select } from "@/app/_components/ui";

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

  const [profile, setProfile] = useState({ displayName: "", avatarUrl: "", theme: "LIGHT" });
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

  const applyMe = useCallback((data: Me) => {
    setMe(data);
    setProfile({
      displayName: data.displayName,
      avatarUrl: data.avatarUrl ?? "",
      theme: data.theme,
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
      .catch(() => setProfileMsg({ ok: "", err: "Sunucuya ulasilamadi." }))
      .finally(() => setLoading(false));
  }, [applyMe]);

  // The saved theme drives the dashboard only. The public media-kit page keeps
  // stamping its own per-kit theme, so a visitor's view never depends on this.
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
        setProfileMsg({ ok: "", err: await errorText(res, "Profil kaydedilemedi.") });
        return;
      }
      applyMe(await res.json());
      setProfileMsg({ ok: "Profil guncellendi.", err: "" });
    } catch {
      setProfileMsg({ ok: "", err: "Sunucuya ulasilamadi." });
    } finally {
      setProfileBusy(false);
    }
  }

  async function changePassword(e: React.FormEvent) {
    e.preventDefault();
    setPwMsg({ ok: "", err: "" });
    // Caught here so a typo never costs a round trip.
    if (pw.newPassword !== pw.repeat) {
      setPwMsg({ ok: "", err: "Yeni sifreler eslesmiyor." });
      return;
    }
    if (pw.newPassword.length < 8) {
      setPwMsg({ ok: "", err: "Yeni sifre en az 8 karakter olmali." });
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
        setPwMsg({ ok: "", err: await errorText(res, "Sifre degistirilemedi.") });
        return;
      }
      setPw({ currentPassword: "", newPassword: "", repeat: "" });
      setPwMsg({ ok: "Sifreniz degistirildi.", err: "" });
    } catch {
      setPwMsg({ ok: "", err: "Sunucuya ulasilamadi." });
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
        setMailMsg({ ok: "", err: await errorText(res, "E-posta degistirilemedi.") });
        return;
      }
      // The token that authenticated this call names the OLD address and is
      // now dead — swapping in the replacement keeps the session alive.
      const data = await res.json();
      localStorage.setItem("token", data.token);
      applyMe(data.user);
      setMail({ currentPassword: "", newEmail: "" });
      setMailMsg({ ok: "E-postaniz guncellendi.", err: "" });
    } catch {
      setMailMsg({ ok: "", err: "Sunucuya ulasilamadi." });
    } finally {
      setMailBusy(false);
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
        setDelErr(await errorText(res, "Hesap silinemedi."));
        return;
      }
      localStorage.removeItem("token");
      window.location.href = "/";
    } catch {
      setDelErr("Sunucuya ulasilamadi.");
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
            <ArrowLeft className="h-4 w-4" /> Panoya don
          </Link>
          <span className="text-sm text-muted">{me.email}</span>
        </div>
      </header>

      <main className="mx-auto max-w-2xl px-5 py-8">
        <h1 className="text-2xl font-semibold tracking-tight">Hesap ayarlari</h1>
        <p className="mt-1 text-sm text-muted">
          Profilinizi, giris bilgilerinizi ve hesabinizi buradan yonetin.
        </p>

        {/* ---------- Profile ---------- */}
        <Card className="mt-8 p-6">
          <h2 className="font-semibold">Profil</h2>
          <p className="mt-1 text-sm text-muted">
            Bu bilgiler yalnizca panonuzda gorunur. Medya kitlerinizin kendi basligi ve
            gorseli ayridir; burayi degistirmek yayindaki sayfalarinizi etkilemez.
          </p>

          <form onSubmit={saveProfile} className="mt-5 grid gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="displayName">Gorunen ad</Label>
              <Input
                id="displayName"
                value={profile.displayName}
                onChange={(e) => setProfile({ ...profile, displayName: e.target.value })}
                maxLength={100}
                required
              />
            </div>

            <div className="grid gap-1.5">
              <Label htmlFor="avatarUrl">Avatar URL</Label>
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
                https:// ile baslayan bir gorsel adresi. Bos birakirsaniz bas harfiniz gosterilir.
              </p>
            </div>

            <div className="grid gap-1.5">
              <Label htmlFor="theme">Pano temasi</Label>
              <Select
                id="theme"
                value={profile.theme}
                onChange={(e) => setProfile({ ...profile, theme: e.target.value })}
                className="w-full"
              >
                <option value="LIGHT">Acik</option>
                <option value="DARK">Koyu</option>
              </Select>
              <p className="text-xs text-faint">
                Yalnizca panoyu etkiler. Yayindaki medya kitiniz kendi temasini kullanmaya devam eder.
              </p>
            </div>

            <div className="flex items-center gap-3">
              <Button type="submit" disabled={profileBusy}>
                {profileBusy ? "..." : "Kaydet"}
              </Button>
              {profileMsg.ok && <span className="text-sm text-success">{profileMsg.ok}</span>}
              {profileMsg.err && <span className="text-sm text-danger">{profileMsg.err}</span>}
            </div>
          </form>
        </Card>

        {/* ---------- Password ---------- */}
        <Card className="mt-6 p-6">
          <h2 className="font-semibold">Sifre degistir</h2>
          <p className="mt-1 text-sm text-muted">Guvenlik icin mevcut sifrenizi de girmelisiniz.</p>

          <form onSubmit={changePassword} className="mt-5 grid gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="currentPassword">Mevcut sifre</Label>
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
              <Label htmlFor="newPassword">Yeni sifre</Label>
              <Input
                id="newPassword"
                type="password"
                autoComplete="new-password"
                value={pw.newPassword}
                onChange={(e) => setPw({ ...pw, newPassword: e.target.value })}
                minLength={8}
                required
              />
              <p className="text-xs text-faint">En az 8 karakter.</p>
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="repeatPassword">Yeni sifre (tekrar)</Label>
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
                {pwBusy ? "..." : "Sifreyi degistir"}
              </Button>
              {pwMsg.ok && <span className="text-sm text-success">{pwMsg.ok}</span>}
              {pwMsg.err && <span className="text-sm text-danger">{pwMsg.err}</span>}
            </div>
          </form>
        </Card>

        {/* ---------- Email ---------- */}
        <Card className="mt-6 p-6">
          <h2 className="font-semibold">E-posta degistir</h2>
          <p className="mt-1 text-sm text-muted">
            Su anki adresiniz: <span className="text-fg">{me.email}</span>
          </p>

          <form onSubmit={changeEmail} className="mt-5 grid gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="newEmail">Yeni e-posta</Label>
              <Input
                id="newEmail"
                type="email"
                value={mail.newEmail}
                onChange={(e) => setMail({ ...mail, newEmail: e.target.value })}
                required
              />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="emailPassword">Mevcut sifre</Label>
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
              Bu adresle giris yapacaksiniz. Dogrulama e-postasi gonderilmedigi icin
              adresi dogru yazdiginizdan emin olun.
            </p>
            <div className="flex items-center gap-3">
              <Button type="submit" disabled={mailBusy}>
                {mailBusy ? "..." : "E-postayi degistir"}
              </Button>
              {mailMsg.ok && <span className="text-sm text-success">{mailMsg.ok}</span>}
              {mailMsg.err && <span className="text-sm text-danger">{mailMsg.err}</span>}
            </div>
          </form>
        </Card>

        {/* ---------- Danger zone ---------- */}
        <Card className="mt-6 border-danger/30 p-6">
          <h2 className="flex items-center gap-2 font-semibold text-danger">
            <ShieldAlert className="h-4 w-4" /> Hesabi sil
          </h2>
          <p className="mt-1 text-sm text-muted">
            Hesabiniz, tum medya kitleriniz, istatistikleriniz ve gelen kutunuz kalici olarak
            silinir. <span className="text-fg">Yayindaki sayfalariniz da erisilemez olur.</span>{" "}
            Bu islem geri alinamaz.
          </p>

          <form onSubmit={deleteAccount} className="mt-5 grid gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="deletePassword">Mevcut sifre</Label>
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
                Onaylamak icin <span className="font-mono text-danger">{DELETE_CONFIRMATION}</span> yazin
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
                {delBusy ? "..." : "Hesabimi kalici olarak sil"}
              </Button>
              {delErr && <span className="text-sm text-danger">{delErr}</span>}
            </div>
          </form>
        </Card>
      </main>
    </div>
  );
}
