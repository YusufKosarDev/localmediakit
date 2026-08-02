"use client";

import { useState } from "react";
import { ArrowDown, ArrowUp, Plus, Trash2 } from "lucide-react";
import { Button, Input, Select } from "@/app/_components/ui";
import { del, get, post, put } from "../_lib/api";
import { useResource } from "../_lib/useResource";
import { PLATFORMS, type Feedback, type MediaItem, type Translate } from "../_lib/types";

const emptyForm = { title: "", url: "", thumbnailUrl: "", platform: "", note: "" };

/**
 * The creator's showcase.
 *
 * <p>Links and cover images rather than uploads: the host's disk is wiped on
 * every deploy, so an upload endpoint would mean either a paid object store or
 * a feature that silently loses files. The work already lives on the platform
 * it was published to, which is where a brand would rather watch it.
 */
export function MediaPanel({
  kitId,
  feedback,
  t,
}: {
  kitId: number;
  feedback: Feedback;
  t: Translate;
}) {
  const [form, setForm] = useState({ ...emptyForm });
  const { data: items, reload, setData } = useResource<MediaItem[]>(
    `media-${kitId}`,
    () => get<MediaItem[]>(`/api/mediakits/${kitId}/media`),
    []
  );

  async function add(e: React.FormEvent) {
    e.preventDefault();
    feedback.clear();
    const result = await post(
      `/api/mediakits/${kitId}/media`,
      { ...form, displayOrder: items.length },
      t("failedAddMedia"),
      201
    );
    if (result.ok) {
      setForm({ ...emptyForm });
      await reload();
    } else {
      feedback.fail(result.message);
    }
  }

  /** @returns whether it saved — the reorder below depends on both writes. */
  async function save(item: MediaItem): Promise<boolean> {
    feedback.clear();
    const result = await put(`/api/mediakits/${kitId}/media/${item.id}`, {
      title: item.title,
      url: item.url,
      thumbnailUrl: item.thumbnailUrl,
      platform: item.platform,
      note: item.note,
      displayOrder: item.displayOrder,
    });
    if (!result.ok) feedback.fail(result.message);
    return result.ok;
  }

  /** Swaps two rows' displayOrder; reloads only if both writes landed. */
  async function move(index: number, dir: -1 | 1) {
    const other = index + dir;
    if (other < 0 || other >= items.length) return;
    const a = { ...items[index], displayOrder: other };
    const b = { ...items[other], displayOrder: index };
    if ((await save(a)) && (await save(b))) await reload();
  }

  async function remove(itemId: number) {
    feedback.clear();
    const result = await del(`/api/mediakits/${kitId}/media/${itemId}`);
    if (result.ok) await reload();
    else feedback.fail(result.message);
  }

  // Rows are edited in place before they are saved; the next reload replaces
  // them with whatever the server kept.
  const patch = (i: number, changes: Partial<MediaItem>) =>
    setData(items.map((x, j) => (j === i ? { ...x, ...changes } : x)));

  return (
    <div className="grid gap-3">
      <div>
        <div className="text-sm font-medium">{t("mediaTitle")}</div>
        <p className="mt-1 text-xs text-muted">{t("mediaHint")}</p>
      </div>

      {items.length === 0 && <p className="text-sm text-muted">{t("mediaNone")}</p>}

      {items.map((item, i) => (
        <div key={item.id} className="flex flex-wrap items-center gap-2">
          <Input
            placeholder={t("mediaFieldTitle")}
            className="w-40"
            value={item.title}
            onChange={(e) => patch(i, { title: e.target.value })}
          />
          <Input
            placeholder={t("mediaFieldUrl")}
            className="w-56"
            value={item.url}
            onChange={(e) => patch(i, { url: e.target.value })}
          />
          <Input
            placeholder={t("mediaFieldThumb")}
            className="w-48"
            value={item.thumbnailUrl ?? ""}
            onChange={(e) => patch(i, { thumbnailUrl: e.target.value })}
          />
          <Select
            value={item.platform ?? ""}
            onChange={(e) => patch(i, { platform: e.target.value })}
          >
            <option value="">{t("mediaPlatformNone")}</option>
            {PLATFORMS.map((p) => (
              <option key={p} value={p}>{p}</option>
            ))}
          </Select>
          <Button size="sm" variant="secondary" onClick={() => save(item)}>{t("save")}</Button>
          <Button size="sm" variant="ghost" onClick={() => move(i, -1)} disabled={i === 0}>
            <ArrowUp className="h-4 w-4" />
          </Button>
          <Button size="sm" variant="ghost" onClick={() => move(i, 1)} disabled={i === items.length - 1}>
            <ArrowDown className="h-4 w-4" />
          </Button>
          <Button size="sm" variant="ghost" onClick={() => remove(item.id)}>
            <Trash2 className="h-4 w-4" />
          </Button>
        </div>
      ))}

      <form onSubmit={add} className="flex flex-wrap items-center gap-2 border-t border-line pt-3">
        <Input
          required
          placeholder={t("mediaFieldTitle")}
          className="w-40"
          value={form.title}
          onChange={(e) => setForm({ ...form, title: e.target.value })}
        />
        <Input
          required
          type="url"
          placeholder={t("mediaFieldUrl")}
          className="w-56"
          value={form.url}
          onChange={(e) => setForm({ ...form, url: e.target.value })}
        />
        <Input
          type="url"
          placeholder={t("mediaFieldThumb")}
          className="w-48"
          value={form.thumbnailUrl}
          onChange={(e) => setForm({ ...form, thumbnailUrl: e.target.value })}
        />
        <Select value={form.platform} onChange={(e) => setForm({ ...form, platform: e.target.value })}>
          <option value="">{t("mediaPlatformNone")}</option>
          {PLATFORMS.map((p) => (
            <option key={p} value={p}>{p}</option>
          ))}
        </Select>
        <Input
          placeholder={t("mediaFieldNote")}
          className="w-40"
          value={form.note}
          onChange={(e) => setForm({ ...form, note: e.target.value })}
        />
        <Button type="submit" size="sm"><Plus className="h-3.5 w-3.5" /> {t("add")}</Button>
      </form>
    </div>
  );
}
