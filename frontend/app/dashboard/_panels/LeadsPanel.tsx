"use client";

import { useCallback, useEffect, useState } from "react";
import { Trash2 } from "lucide-react";
import { Badge, Button } from "@/app/_components/ui";
import { del, get, put } from "../_lib/api";
import type { Feedback, Lead } from "../_lib/types";

/** Brand enquiries that arrived through the public page's contact form. */
export function LeadsPanel({ kitId, feedback }: { kitId: number; feedback: Feedback }) {
  const [leads, setLeads] = useState<Lead[]>([]);

  const load = useCallback(async () => {
    const data = await get<Lead[]>(`/api/mediakits/${kitId}/leads`);
    if (data) setLeads(data);
  }, [kitId]);

  useEffect(() => {
    load();
  }, [load]);

  async function setStatus(leadId: number, status: string) {
    feedback.clear();
    const result = await put(`/api/mediakits/${kitId}/leads/${leadId}/status`, { status }, "Guncellenemedi");
    if (result.ok) await load();
    else feedback.fail(result.message);
  }

  async function remove(leadId: number) {
    feedback.clear();
    const result = await del(`/api/mediakits/${kitId}/leads/${leadId}`);
    if (result.ok) await load();
    else feedback.fail(result.message);
  }

  return (
    <div className="grid gap-2.5">
      {leads.length === 0 && (
        <p className="text-sm text-muted">
          Henuz marka teklifi yok. Public sayfadaki iletisim formundan gelen teklifler burada listelenir.
        </p>
      )}
      {leads.map((l) => (
        <div
          key={l.id}
          className={`rounded-lg border border-line bg-surface p-3 text-sm ${l.status === "ARCHIVED" ? "opacity-60" : ""}`}
        >
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-medium">{l.brandName}</span>
            <a href={`mailto:${l.email}`} className="text-brand hover:underline">{l.email}</a>
            {l.status === "NEW" && <Badge tone="brand">yeni</Badge>}
            {l.status === "ARCHIVED" && <Badge tone="neutral">arsiv</Badge>}
            <span className="ml-auto text-xs text-faint">
              {new Date(l.createdAt).toLocaleString("tr-TR")}
            </span>
          </div>
          <p className="mt-1.5 whitespace-pre-wrap text-muted">{l.message}</p>
          <div className="mt-2 flex gap-2">
            {l.status === "NEW" && (
              <Button size="sm" variant="secondary" onClick={() => setStatus(l.id, "READ")}>Okundu</Button>
            )}
            {l.status !== "ARCHIVED" && (
              <Button size="sm" variant="ghost" onClick={() => setStatus(l.id, "ARCHIVED")}>Arsivle</Button>
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
