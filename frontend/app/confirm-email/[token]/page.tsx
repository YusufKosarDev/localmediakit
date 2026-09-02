"use client";

import { use, useEffect, useState } from "react";
import Link from "next/link";
import { Button, Card } from "@/app/_components/ui";
import { LocaleSwitch } from "@/app/_components/LocaleSwitch";
import { translator } from "@/app/_i18n";
import { appDict } from "@/app/_i18n/app";
import { useStoredLocale } from "@/app/_i18n/useLocale";

const BACKEND = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";

/**
 * Confirming a new sign-in address.
 *
 * <p>Unlike the password reset page, this one spends the token on load rather
 * than behind a button. There is nothing to ask the visitor for: they proved
 * who they were with a password before the mail was sent, and the only thing
 * left is whether they can read mail at the address they typed. A form here
 * would be a button that says "yes, the thing you clicked".
 *
 * <p>Which does mean a link-prefetching mail client can spend the token before
 * a person sees the page — and that is the right outcome, not a hazard: the
 * prefetch happened inside the mailbox being verified, which is exactly what
 * the link is asking to prove.
 *
 * <p>No session comes back from this. Confirming shows control of a mailbox,
 * not ownership of the account, and handing out a token here would turn a
 * forwarded mail into full access. The visitor signs in with the new address.
 */
export default function ConfirmEmailPage({
  params,
}: {
  params: Promise<{ token: string }>;
}) {
  const { token } = use(params);
  const [state, setState] = useState<"working" | "done" | "failed">("working");
  const [locale, setLocale] = useStoredLocale();
  const t = translator(appDict, locale);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await fetch(`${BACKEND}/api/auth/email-change/confirm`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ token }),
        });
        if (!cancelled) setState(res.ok ? "done" : "failed");
      } catch {
        if (!cancelled) setState("failed");
      }
      // The old session names the old address and is dead either way; clearing
      // it here means the sign-in link below lands on a sign-in form rather
      // than bouncing off a dashboard that cannot load.
      try {
        window.localStorage.removeItem("token");
      } catch {
        // A browser refusing storage is not a reason to fail the confirmation.
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [token]);

  return (
    <main className="grid min-h-screen place-items-center bg-page px-5 text-fg">
      <div className="w-full max-w-sm">
        <div className="mb-3 flex justify-end">
          <LocaleSwitch locale={locale} onChange={setLocale} label={t("langLabel")} />
        </div>
        <Card className="p-7">
          <h1 className="text-lg font-semibold tracking-tight">{t("confirmEmailTitle")}</h1>

          {state === "working" && (
            <p className="mt-4 text-sm text-muted">{t("confirmEmailWorking")}</p>
          )}

          {state === "done" && (
            <>
              <p className="mt-4 rounded-lg bg-brand-weak px-3 py-2.5 text-sm text-brand">
                {t("confirmEmailDone")}
              </p>
              <Link href="/login" className="mt-4 block">
                <Button className="w-full">{t("navSignIn")}</Button>
              </Link>
            </>
          )}

          {state === "failed" && (
            <>
              <p className="mt-4 text-sm text-danger">{t("confirmEmailInvalid")}</p>
              <p className="mt-4 text-center text-sm">
                <Link href="/login" className="text-brand hover:underline">
                  {t("navSignIn")}
                </Link>
              </p>
            </>
          )}
        </Card>
      </div>
    </main>
  );
}
