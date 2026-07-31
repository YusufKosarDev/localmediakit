"use client";

import { useCallback, useEffect, useState } from "react";
import { Globe, RefreshCw } from "lucide-react";
import { Badge, Button, Input } from "@/app/_components/ui";
import { del, get, post } from "../_lib/api";
import { formatDateTime, type Locale } from "@/app/_i18n";
import type { Domain, Feedback, Translate } from "../_lib/types";

function toneFor(status: string) {
  if (status === "VERIFIED") return "success" as const;
  if (status === "FAILED") return "danger" as const;
  return "warning" as const;
}

/** Custom-domain DNS verification. The feature itself is still "coming soon". */
export function DomainPanel({ kitId, feedback, t, locale }: { kitId: number; feedback: Feedback; t: Translate; locale: Locale }) {
  const [domains, setDomains] = useState<Domain[]>([]);
  const [input, setInput] = useState("");

  const load = useCallback(async () => {
    const data = await get<Domain[]>(`/api/mediakits/${kitId}/domains`);
    if (data) {
      setDomains(data);
      setInput("");
    }
  }, [kitId]);

  useEffect(() => {
    load();
  }, [load]);

  async function add(e: React.FormEvent) {
    e.preventDefault();
    feedback.clear();
    const result = await post(`/api/mediakits/${kitId}/domains`, { domain: input }, t("failedAddDomain"), 201);
    if (result.ok) {
      setInput("");
      await load();
    } else {
      feedback.fail(result.message);
    }
  }

  async function check(domainId: number) {
    feedback.clear();
    const result = await post(`/api/mediakits/${kitId}/domains/${domainId}/check`, undefined, t("failedCheckDomain"));
    if (result.ok) await load();
    else feedback.fail(result.message);
  }

  async function remove(domainId: number) {
    feedback.clear();
    const result = await del(`/api/mediakits/${kitId}/domains/${domainId}`);
    if (result.ok) await load();
    else feedback.fail(result.message);
  }

  return (
    <div className="grid gap-3">
      <div className="flex items-center gap-2">
        <span className="text-sm font-medium">{t("customDomain")}</span>
        <Badge tone="warning">{t("comingSoon")}</Badge>
      </div>
      <p className="text-xs text-muted">{t("domainIntro")}</p>
      <form onSubmit={add} className="flex flex-wrap gap-2">
        <Input
          placeholder={t("domainPlaceholder")}
          className="w-64"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          required
        />
        <Button type="submit" size="sm"><Globe className="h-3.5 w-3.5" /> {t("add")}</Button>
      </form>
      {domains.map((d) => (
        <div key={d.id} className="rounded-lg border border-line bg-surface p-3 text-sm">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-medium">{d.domain}</span>
            <Badge tone={toneFor(d.status)}>{d.status}</Badge>
            {d.lastCheckedAt && (
              <span className="text-xs text-faint">
                {t("domainLastChecked", { when: formatDateTime(d.lastCheckedAt, locale), attempts: d.attempts })}
              </span>
            )}
          </div>
          {d.status !== "VERIFIED" && (
            <div className="mt-2 rounded-lg bg-page p-2.5 text-xs text-muted">
              {t("domainDnsHint")}
              <div className="mt-1 break-all font-mono text-fg">
                {d.dnsRecordHost} = {d.dnsRecordValue}
              </div>
            </div>
          )}
          <div className="mt-2 flex gap-2">
            <Button size="sm" variant="secondary" onClick={() => check(d.id)}>
              <RefreshCw className="h-3.5 w-3.5" /> {t("domainCheckNow")}
            </Button>
            <Button size="sm" variant="ghost" onClick={() => remove(d.id)}>{t("domainRemove")}</Button>
          </div>
        </div>
      ))}
    </div>
  );
}
