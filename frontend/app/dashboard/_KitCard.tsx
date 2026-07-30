"use client";

import { ExternalLink, Eye, Lock, Send, Trash2 } from "lucide-react";
import { Badge, Button, Card } from "@/app/_components/ui";
import { TABS, type Feedback, type Kit, type Tab } from "./_lib/types";
import { EditPanel } from "./_panels/EditPanel";
import { StatsPanel } from "./_panels/StatsPanel";
import { CollabsPanel } from "./_panels/CollabsPanel";
import { LeadsPanel } from "./_panels/LeadsPanel";
import { AnalyticsPanel } from "./_panels/AnalyticsPanel";
import { VersionsPanel } from "./_panels/VersionsPanel";
import { DomainPanel } from "./_panels/DomainPanel";

/**
 * One kit in the list: its header, actions, tab strip, and whichever panel is
 * open.
 *
 * <p>Panels fetch their own data, so this component only routes to them —
 * which is why opening a tab needs nothing more than recording which one it
 * is.
 */
export function KitCard({
  kit,
  openTab,
  versionsToken,
  feedback,
  onPreview,
  onPublish,
  onDelete,
  onTabSelect,
  onKitField,
  onKitSaved,
  onProgressChanged,
}: {
  kit: Kit;
  /** The open tab for this kit, or null when it is collapsed. */
  openTab: Tab | null;
  versionsToken: number;
  feedback: Feedback;
  onPreview: () => void;
  onPublish: () => void;
  onDelete: () => void;
  onTabSelect: (tab: Tab) => void;
  onKitField: (field: keyof Kit, value: string | boolean) => void;
  onKitSaved: () => Promise<void> | void;
  onProgressChanged: () => Promise<void> | void;
}) {
  return (
    <Card className="overflow-hidden">
      {/* Header */}
      <div className="flex flex-wrap items-center gap-3 p-4">
        <div className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-brand-weak font-semibold text-brand">
          {(kit.title || "?").charAt(0).toUpperCase()}
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <span className="truncate font-medium">{kit.title}</span>
            <Badge tone={kit.status === "PUBLISHED" ? "success" : "neutral"}>{kit.status}</Badge>
            {kit.passwordProtected && (
              <Badge tone="warning"><Lock className="h-3 w-3" /> sifreli</Badge>
            )}
          </div>
          {kit.publishedSlug && (
            <a
              href={`/${kit.publishedSlug}`}
              target="_blank"
              rel="noreferrer"
              className="mt-0.5 inline-flex items-center gap-1 text-xs text-muted hover:text-brand"
            >
              /{kit.publishedSlug} <ExternalLink className="h-3 w-3" />
            </a>
          )}
        </div>
        <div className="flex items-center gap-2">
          <Button size="sm" variant="secondary" onClick={onPreview}>
            <Eye className="h-3.5 w-3.5" /> Onizle
          </Button>
          <Button size="sm" onClick={onPublish}>
            <Send className="h-3.5 w-3.5" /> Yayinla
          </Button>
          <Button size="sm" variant="danger" onClick={onDelete}>
            <Trash2 className="h-3.5 w-3.5" />
          </Button>
        </div>
      </div>

      {/* Tab strip */}
      <div className="flex gap-1 overflow-x-auto border-t border-line px-2 py-1.5">
        {TABS.map((t) => {
          const on = openTab === t.id;
          return (
            <button
              key={t.id}
              onClick={() => onTabSelect(t.id)}
              className={`shrink-0 rounded-lg px-3 py-1.5 text-sm transition-colors ${
                on ? "bg-brand-weak font-medium text-brand" : "text-muted hover:text-fg hover:bg-page"
              }`}
            >
              {t.label}
              {t.id === "domain" ? " ·" : ""}
            </button>
          );
        })}
      </div>

      {/* Tab content */}
      {openTab && (
        <div className="border-t border-line bg-page/40 p-4">
          {openTab === "edit" && (
            <EditPanel kit={kit} feedback={feedback} onField={onKitField} onSaved={onKitSaved} />
          )}
          {openTab === "stats" && (
            <StatsPanel kitId={kit.id} feedback={feedback} onStatsChanged={onProgressChanged} />
          )}
          {openTab === "collabs" && <CollabsPanel kitId={kit.id} feedback={feedback} />}
          {openTab === "leads" && <LeadsPanel kitId={kit.id} feedback={feedback} />}
          {openTab === "analytics" && <AnalyticsPanel kitId={kit.id} feedback={feedback} />}
          {openTab === "versions" && (
            <VersionsPanel
              kitId={kit.id}
              reloadToken={versionsToken}
              feedback={feedback}
              onActivated={onKitSaved}
            />
          )}
          {openTab === "domain" && <DomainPanel kitId={kit.id} feedback={feedback} />}
        </div>
      )}
    </Card>
  );
}
