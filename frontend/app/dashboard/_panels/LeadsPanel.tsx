"use client";

import { Download, Trash2 } from "lucide-react";
import { Badge, Button } from "@/app/_components/ui";
import { BACKEND, authHeaders, del, errorMessage, get, put } from "../_lib/api";
import { useResource } from "../_lib/useResource";
import { formatDateTime, type Locale } from "@/app/_i18n";
import type { Feedback, Lead, Translate } from "../_lib/types";

/** Brand enquiries that arrived through the public page's contact form. */
export function LeadsPanel({ kitId, feedback, t, locale }: { kitId: number; feedback: Feedback; t: Translate; locale: Locale }) {
  const { data: leads, reload } = useResource<Lead[]>(
    `leads-${kitId}`,
    () => get<Lead[]>(`/api/mediakits/${kitId}/leads`),
    []
  );

  async function setStatus(leadId: number, status: string) {
    feedback.clear();
    const result = await put(`/api/mediakits/${kitId}/leads/${leadId}/status`, { status }, t("failedUpdate"));
    if (result.ok) await reload();
    else feedback.fail(result.message);
  }

  async function remove(leadId: number) {
    feedback.clear();
    const result = await del(`/api/mediakits/${kitId}/leads/${leadId}`);
    if (result.ok) await reload();
    else feedback.fail(result.message);
  }

  /**
   * Fetched rather than linked. The endpoint needs the bearer token, which an
   * anchor cannot send, so the file is pulled with the same headers as every
   * other call and handed to the browser as a blob.
   */
  async function exportCsv() {
    feedback.clear();
    try {
      const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/leads/export`, {
        headers: authHeaders(),
      });
      if (!res.ok) {
        feedback.fail(await errorMessage(res, t("failedExport")));
        return;
      }
      const url = URL.createObjectURL(await res.blob());
      const link = document.createElement("a");
      link.href = url;
      link.download = `leads-${kitId}.csv`;
      link.click();
      // The object URL pins the blob in memory until it is released.
      URL.revokeObjectURL(url);
    } catch {
      feedback.fail(t("failedExport"));
    }
  }

  return (
    <div className="grid gap-2.5">
      {leads.length === 0 && (
        <p className="text-sm text-muted">
          {t("noLeads")}
        </p>
      )}
      {leads.length > 0 && (
        <div className="flex justify-end">
          <Button size="sm" variant="secondary" onClick={exportCsv}>
            <Download className="h-3.5 w-3.5" /> {t("leadExport")}
          </Button>
        </div>
      )}
      {leads.map((l) => (
        <div
          key={l.id}
          className={`rounded-lg border border-line bg-surface p-3 text-sm ${l.status === "ARCHIVED" ? "opacity-60" : ""}`}
        >
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-medium">{l.brandName}</span>
            <a href={`mailto:${l.email}`} className="text-brand hover:underline">{l.email}</a>
            {l.status === "NEW" && <Badge tone="brand">{t("leadNew")}</Badge>}
            {l.status === "ARCHIVED" && <Badge tone="neutral">{t("leadArchived")}</Badge>}
            <span className="ml-auto text-xs text-faint">
              {formatDateTime(l.createdAt, locale)}
            </span>
          </div>
          <p className="mt-1.5 whitespace-pre-wrap text-muted">{l.message}</p>
          <div className="mt-2 flex gap-2">
            {l.status === "NEW" && (
              <Button size="sm" variant="secondary" onClick={() => setStatus(l.id, "READ")}>{t("leadMarkRead")}</Button>
            )}
            {l.status !== "ARCHIVED" && (
              <Button size="sm" variant="ghost" onClick={() => setStatus(l.id, "ARCHIVED")}>{t("leadArchive")}</Button>
            )}
            <Button size="sm" variant="ghost" onClick={() => remove(l.id)}>
              <Trash2 className="h-4 w-4" />
            </Button>
          </div>
        </div>
      ))}
    </div>
  );
}
