"use client";

import { useEffect, useState } from "react";
import { Download, X } from "lucide-react";
import { Button, Card } from "@/app/_components/ui";

/** Chrome/Edge fire this instead of prompting on their own. */
type InstallEvent = Event & {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
};

const DISMISS_KEY = "lmk.install.dismissed";

/**
 * A quiet offer to install, shown only once the browser has decided the app
 * qualifies. It never interrupts: no modal, no overlay — a card at the bottom
 * of the dashboard that stays gone once waved away.
 *
 * <p>Deliberately client-only state. Whether an app is installed is a property
 * of the device, not the account, so storing this server-side would hide the
 * offer on a phone because the user once dismissed it on a laptop.
 */
export function InstallPrompt() {
  const [event, setEvent] = useState<InstallEvent | null>(null);
  const [hidden, setHidden] = useState(true);

  useEffect(() => {
    if (localStorage.getItem(DISMISS_KEY)) return;
    const onPrompt = (e: Event) => {
      // Holding the event back is what lets us choose the moment; without
      // this the browser shows its own mini-infobar and moves on.
      e.preventDefault();
      setEvent(e as InstallEvent);
      setHidden(false);
    };
    window.addEventListener("beforeinstallprompt", onPrompt);
    return () => window.removeEventListener("beforeinstallprompt", onPrompt);
  }, []);

  function dismiss() {
    setHidden(true);
    localStorage.setItem(DISMISS_KEY, "1");
  }

  async function install() {
    if (!event) return;
    setHidden(true);
    await event.prompt();
    // Either way the offer is spent: the event cannot be reused.
    await event.userChoice.catch(() => null);
    localStorage.setItem(DISMISS_KEY, "1");
  }

  if (hidden || !event) return null;

  return (
    <Card className="mt-6 flex flex-wrap items-center gap-3 p-4">
      <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-brand-weak text-brand">
        <Download className="h-4 w-4" />
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium">Panoyu ana ekraniniza ekleyin</p>
        <p className="mt-0.5 text-xs text-muted">
          Analitiginizi telefonunuzdan tek dokunusla acin. Tarayicidan kullanmaya
          devam edebilirsiniz — bir sey degismez.
        </p>
      </div>
      <div className="flex items-center gap-2">
        <Button size="sm" onClick={install}>
          Ekle
        </Button>
        <button
          onClick={dismiss}
          aria-label="Kapat"
          className="rounded-lg p-1.5 text-muted transition-colors hover:bg-page hover:text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand/50"
        >
          <X className="h-4 w-4" />
        </button>
      </div>
    </Card>
  );
}
