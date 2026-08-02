"use client";

import { useState } from "react";
import { Badge, Button, Select } from "@/app/_components/ui";
import { BACKEND, authHeaders, get, post } from "../_lib/api";
import { useResource } from "../_lib/useResource";
import { formatDateTime, type Locale } from "@/app/_i18n";
import type { Feedback, Version, VersionDiff, Translate } from "../_lib/types";

/** True when two snapshots differ in nothing the diff reports. */
function isEmptyDiff(diff: VersionDiff): boolean {
  const groups = [diff.collaborations, diff.rateCard, diff.demographics];
  return (
    diff.fields.length === 0 &&
    diff.platforms.length === 0 &&
    groups.every((g) => g.added.length + g.removed.length + g.changed.length === 0)
  );
}

/** Published history, rollback, and a diff between any two snapshots. */
export function VersionsPanel({
  kitId,
  reloadToken,
  feedback,
  t,
  locale,
  onActivated,
}: {
  kitId: number;
  /** Bumped by the shell after a publish, so the list refreshes in place. */
  reloadToken: number;
  feedback: Feedback;
  t: Translate;
  locale: Locale;
  onActivated: () => Promise<void> | void;
}) {
  const [diffSel, setDiffSel] = useState({ from: "", to: "" });
  const [diff, setDiff] = useState<VersionDiff | null>(null);

  // reloadToken is a dependency rather than a second effect: a publish adds a
  // version, so the list has to be re-read for the same reason a kit switch does.
  const { data: versions, reload } = useResource<Version[]>(
    // reloadToken is part of the key rather than a second effect: a publish adds
    // a version, so the list has to be re-read for the same reason a kit switch
    // does, and both are just "this is a different list now".
    `versions-${kitId}-${reloadToken}`,
    () => {
      // A comparison is between two specific versions; once the list behind it
      // can have changed, showing the old result would be showing a diff of
      // something the user is no longer looking at.
      setDiff(null);
      setDiffSel({ from: "", to: "" });
      return get<Version[]>(`/api/mediakits/${kitId}/versions`);
    },
    []
  );

  async function loadDiff() {
    feedback.clear();
    setDiff(null);
    const res = await fetch(
      `${BACKEND}/api/mediakits/${kitId}/versions/diff?from=${diffSel.from}&to=${diffSel.to}`,
      { headers: authHeaders() }
    );
    if (res.ok) setDiff(await res.json());
    else feedback.fail(`${t("failedCompare")} (HTTP ${res.status})`);
  }

  async function activate(version: number) {
    feedback.clear();
    const result = await post(`/api/mediakits/${kitId}/versions/${version}/activate`, undefined, t("failedRollback"));
    if (result.ok) {
      await onActivated();
      await reload();
    } else {
      feedback.fail(result.message);
    }
  }

  return (
    <div className="grid gap-2">
      {versions.length === 0 && <p className="text-sm text-muted">{t("notPublishedYet")}</p>}
      {versions.map((v) => (
        <div
          key={v.version}
          className="flex flex-wrap items-center gap-3 rounded-lg border border-line bg-surface px-3 py-2 text-sm"
        >
          <span className="font-medium">v{v.version}</span>
          <span className="text-muted">/{v.slug}</span>
          <span className="text-xs text-faint">{formatDateTime(v.publishedAt, locale)}</span>
          {v.active ? (
            <Badge tone="success">{t("live")}</Badge>
          ) : (
            <Button size="sm" variant="secondary" onClick={() => activate(v.version)}>
              {t("rollback")}
            </Button>
          )}
        </div>
      ))}

      {versions.length >= 2 && (
        <div className="mt-2 rounded-lg border border-line bg-surface p-3">
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <span className="font-medium">{t("compare")}</span>
            <Select value={diffSel.from} onChange={(e) => setDiffSel({ ...diffSel, from: e.target.value })}>
              <option value="">{t("compareOlder")}</option>
              {versions.map((v) => (
                <option key={v.version} value={v.version}>v{v.version}</option>
              ))}
            </Select>
            <span className="text-muted">→</span>
            <Select value={diffSel.to} onChange={(e) => setDiffSel({ ...diffSel, to: e.target.value })}>
              <option value="">{t("compareNewer")}</option>
              {versions.map((v) => (
                <option key={v.version} value={v.version}>v{v.version}</option>
              ))}
            </Select>
            <Button
              size="sm"
              disabled={!diffSel.from || !diffSel.to || diffSel.from === diffSel.to}
              onClick={loadDiff}
            >
              {t("compareShow")}
            </Button>
          </div>

          {diff && (
            <div className="mt-3 grid gap-1.5 border-t border-line pt-3 text-sm">
              {isEmptyDiff(diff) && (
                <p className="text-muted">
                  {t("noDiff", { from: diff.fromVersion, to: diff.toVersion })}
                </p>
              )}
              {diff.fields.map((f) => (
                <div key={f.field}>
                  <span className="text-faint">{f.field}:</span>{" "}
                  <span className="text-danger line-through">{f.from ?? "—"}</span>{" "}
                  <span className="text-success">{f.to ?? "—"}</span>
                </div>
              ))}
              {diff.platforms.map((p) => (
                <div key={p.platform}>
                  <span className="font-medium">{p.platform}</span>{" "}
                  {p.kind === "ADDED" && <Badge tone="success">{t("diffAdded")}</Badge>}
                  {p.kind === "REMOVED" && <Badge tone="danger">{t("diffRemoved")}</Badge>}
                  {p.changes.map((c) => (
                    <span key={c.metric} className="ml-2 text-muted">
                      {c.metric}: {c.from ?? "—"} → <span className="text-fg">{c.to ?? "—"}</span>
                    </span>
                  ))}
                </div>
              ))}
              {(
                [
                  [t("diffCollabs"), diff.collaborations],
                  [t("diffRates"), diff.rateCard],
                  [t("diffDemographics"), diff.demographics],
                ] as const
              )
                .filter(([, d]) => d.added.length + d.removed.length + d.changed.length > 0)
                .map(([label, d]) => (
                  <div key={label}>
                    <span className="font-medium">{label}:</span>{" "}
                    {d.added.length > 0 && <span className="text-success">+{d.added.join(", ")}</span>}{" "}
                    {d.removed.length > 0 && <span className="text-danger">−{d.removed.join(", ")}</span>}{" "}
                    {d.changed.map((c) => (
                      <span key={c.metric} className="ml-1 text-muted">
                        {c.metric}: {c.from} → <span className="text-fg">{c.to}</span>
                      </span>
                    ))}
                  </div>
                ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
