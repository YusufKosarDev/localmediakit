"use client";

import { useState } from "react";
import { Copy, Link2, Plus } from "lucide-react";
import { Badge, Button, Input } from "@/app/_components/ui";
import { del, get, post } from "../_lib/api";
import { useResource } from "../_lib/useResource";
import { formatDateTime, type Locale } from "@/app/_i18n";
import type { Feedback, ShareLink, Translate } from "../_lib/types";

/**
 * Per-brand links, and what came back through them.
 *
 * <p>This is the half of the product that used to be missing: the page could be
 * sent to a brand and the brand could read it, and the creator saw a number.
 * The label is theirs to write, so nothing here learns anything about a visitor
 * that the anonymous fingerprint already refuses to learn.
 */
export function ShareLinksPanel({
  kitId,
  publishedSlug,
  feedback,
  t,
  locale,
}: {
  kitId: number;
  /** Null until the kit is published; a link to nothing is not worth handing out. */
  publishedSlug: string | null;
  feedback: Feedback;
  t: Translate;
  locale: Locale;
}) {
  const [label, setLabel] = useState("");
  const { data: links, reload } = useResource<ShareLink[]>(
    `share-links-${kitId}`,
    () => get<ShareLink[]>(`/api/mediakits/${kitId}/share-links`),
    []
  );

  async function create(e: React.FormEvent) {
    e.preventDefault();
    feedback.clear();
    const result = await post(
      `/api/mediakits/${kitId}/share-links`,
      { label },
      t("failedCreateShareLink"),
      201
    );
    if (result.ok) {
      setLabel("");
      await reload();
    } else {
      feedback.fail(result.message);
    }
  }

  async function revoke(linkId: number) {
    feedback.clear();
    const result = await del(`/api/mediakits/${kitId}/share-links/${linkId}`, t("failedRevokeShareLink"));
    if (result.ok) await reload();
    else feedback.fail(result.message);
  }

  async function copy(link: ShareLink) {
    // The backend sends a relative URL so it never has to know this origin.
    const absolute = `${window.location.origin}${link.url}`;
    try {
      await navigator.clipboard.writeText(absolute);
      feedback.notify(t("shareLinkCopied"));
    } catch {
      // Clipboard access can be refused (insecure context, permissions). Showing
      // the link is a worse experience than copying it, but it is not a dead end.
      feedback.notify(absolute);
    }
  }

  return (
    <div className="grid gap-3">
      <div>
        <div className="flex items-center gap-2 text-sm font-medium">
          <Link2 className="h-3.5 w-3.5 text-brand" /> {t("shareLinksTitle")}
        </div>
        <p className="mt-1 text-xs text-muted">{t("shareLinksHint")}</p>
      </div>

      {links.length === 0 && <p className="text-sm text-muted">{t("shareLinkNone")}</p>}

      {links.map((link) => (
        <div
          key={link.id}
          className="flex flex-wrap items-center gap-3 rounded-lg border border-line bg-surface px-3 py-2 text-sm"
        >
          <span className="font-medium">{link.label}</span>
          {link.views > 0 ? (
            <Badge tone="success">
              {t("shareLinkOpened", { views: link.views, visitors: link.uniqueVisitors })}
            </Badge>
          ) : (
            <span className="text-xs text-faint">{t("shareLinkNeverOpened")}</span>
          )}
          <span className="text-xs text-faint">{formatDateTime(link.createdAt, locale)}</span>
          {link.active ? (
            <>
              <Button size="sm" variant="secondary" onClick={() => copy(link)}>
                <Copy className="h-3.5 w-3.5" /> {t("shareLinkCopy")}
              </Button>
              <Button size="sm" variant="ghost" onClick={() => revoke(link.id)}>
                {t("shareLinkRevoke")}
              </Button>
            </>
          ) : (
            <Badge tone="warning">{t("shareLinkRevoked")}</Badge>
          )}
        </div>
      ))}

      {/* Creating a link before there is a page to open would hand someone a
          404 with their name on it. */}
      {publishedSlug && (
        <form onSubmit={create} className="flex flex-wrap items-center gap-2 border-t border-line pt-3">
          <Input
            required
            maxLength={120}
            placeholder={t("shareLinkLabelPlaceholder")}
            className="w-48"
            value={label}
            onChange={(e) => setLabel(e.target.value)}
          />
          <Button type="submit" size="sm">
            <Plus className="h-3.5 w-3.5" /> {t("shareLinkCreate")}
          </Button>
        </form>
      )}
    </div>
  );
}
