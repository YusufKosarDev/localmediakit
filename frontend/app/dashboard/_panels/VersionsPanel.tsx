"use client";

import { useCallback, useEffect, useState } from "react";
import { Badge, Button, Select } from "@/app/_components/ui";
import { BACKEND, authHeaders, get, post } from "../_lib/api";
import type { Feedback, Version, VersionDiff } from "../_lib/types";

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
  onActivated,
}: {
  kitId: number;
  /** Bumped by the shell after a publish, so the list refreshes in place. */
  reloadToken: number;
  feedback: Feedback;
  onActivated: () => Promise<void> | void;
}) {
  const [versions, setVersions] = useState<Version[]>([]);
  const [diffSel, setDiffSel] = useState({ from: "", to: "" });
  const [diff, setDiff] = useState<VersionDiff | null>(null);

  const load = useCallback(async () => {
    const data = await get<Version[]>(`/api/mediakits/${kitId}/versions`);
    if (data) setVersions(data);
    setDiff(null);
    setDiffSel({ from: "", to: "" });
  }, [kitId]);

  useEffect(() => {
    load();
  }, [load, reloadToken]);

  async function loadDiff() {
    feedback.clear();
    setDiff(null);
    const res = await fetch(
      `${BACKEND}/api/mediakits/${kitId}/versions/diff?from=${diffSel.from}&to=${diffSel.to}`,
      { headers: authHeaders() }
    );
    if (res.ok) setDiff(await res.json());
    else feedback.fail(`Karsilastirilamadi (HTTP ${res.status})`);
  }

  async function activate(version: number) {
    feedback.clear();
    const result = await post(`/api/mediakits/${kitId}/versions/${version}/activate`, undefined, "Versiyona donulemedi");
    if (result.ok) {
      await onActivated();
      await load();
    } else {
      feedback.fail(result.message);
    }
  }

  return (
    <div className="grid gap-2">
      {versions.length === 0 && <p className="text-sm text-muted">Henuz yayinlanmamis.</p>}
      {versions.map((v) => (
        <div
          key={v.version}
          className="flex flex-wrap items-center gap-3 rounded-lg border border-line bg-surface px-3 py-2 text-sm"
        >
          <span className="font-medium">v{v.version}</span>
          <span className="text-muted">/{v.slug}</span>
          <span className="text-xs text-faint">{new Date(v.publishedAt).toLocaleString("tr-TR")}</span>
          {v.active ? (
            <Badge tone="success">yayinda</Badge>
          ) : (
            <Button size="sm" variant="secondary" onClick={() => activate(v.version)}>
              Bu versiyona don
            </Button>
          )}
        </div>
      ))}

      {versions.length >= 2 && (
        <div className="mt-2 rounded-lg border border-line bg-surface p-3">
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <span className="font-medium">Karsilastir:</span>
            <Select value={diffSel.from} onChange={(e) => setDiffSel({ ...diffSel, from: e.target.value })}>
              <option value="">eski...</option>
              {versions.map((v) => (
                <option key={v.version} value={v.version}>v{v.version}</option>
              ))}
            </Select>
            <span className="text-muted">→</span>
            <Select value={diffSel.to} onChange={(e) => setDiffSel({ ...diffSel, to: e.target.value })}>
              <option value="">yeni...</option>
              {versions.map((v) => (
                <option key={v.version} value={v.version}>v{v.version}</option>
              ))}
            </Select>
            <Button
              size="sm"
              disabled={!diffSel.from || !diffSel.to || diffSel.from === diffSel.to}
              onClick={loadDiff}
            >
              Goster
            </Button>
          </div>

          {diff && (
            <div className="mt-3 grid gap-1.5 border-t border-line pt-3 text-sm">
              {isEmptyDiff(diff) && (
                <p className="text-muted">
                  v{diff.fromVersion} ile v{diff.toVersion} arasinda icerik farki yok.
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
                  {p.kind === "ADDED" && <Badge tone="success">eklendi</Badge>}
                  {p.kind === "REMOVED" && <Badge tone="danger">cikti</Badge>}
                  {p.changes.map((c) => (
                    <span key={c.metric} className="ml-2 text-muted">
                      {c.metric}: {c.from ?? "—"} → <span className="text-fg">{c.to ?? "—"}</span>
                    </span>
                  ))}
                </div>
              ))}
              {(
                [
                  ["Isbirlikleri", diff.collaborations],
                  ["Ucretler", diff.rateCard],
                  ["Demografi", diff.demographics],
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
