"use client";

import { useCallback, useEffect, useState } from "react";
import { ArrowDown, ArrowUp, Plus, Trash2 } from "lucide-react";
import { Button, Input, Select } from "@/app/_components/ui";
import { del, get, post, put } from "../_lib/api";
import type { Collab, Feedback, RateItem } from "../_lib/types";

const CURRENCIES = ["TRY", "USD", "EUR"];
const emptyCollabForm = { brandName: "", campaign: "", period: "", resultNote: "" };
const emptyRateForm = { serviceName: "", priceAmount: "", currency: "TRY", note: "" };

/**
 * Past brand collaborations and the rate card.
 *
 * <p>Both belong to the "Isbirlikleri & Ucretler" tab, so both are here: they
 * are two lists on one screen, and the file stays readable at this size.
 */
export function CollabsPanel({ kitId, feedback }: { kitId: number; feedback: Feedback }) {
  const [collabs, setCollabs] = useState<Collab[]>([]);
  const [rates, setRates] = useState<RateItem[]>([]);
  const [collabForm, setCollabForm] = useState({ ...emptyCollabForm });
  const [rateForm, setRateForm] = useState({ ...emptyRateForm });

  const load = useCallback(async () => {
    const [c, r] = await Promise.all([
      get<Collab[]>(`/api/mediakits/${kitId}/collaborations`),
      get<RateItem[]>(`/api/mediakits/${kitId}/ratecard`),
    ]);
    if (c) setCollabs(c);
    if (r) setRates(r);
    setCollabForm({ ...emptyCollabForm });
    setRateForm({ ...emptyRateForm });
  }, [kitId]);

  useEffect(() => {
    load();
  }, [load]);

  async function addCollab(e: React.FormEvent) {
    e.preventDefault();
    feedback.clear();
    const result = await post(
      `/api/mediakits/${kitId}/collaborations`,
      { ...collabForm, displayOrder: collabs.length },
      "Isbirligi eklenemedi",
      201
    );
    if (result.ok) await load();
    else feedback.fail(result.message);
  }

  /** @returns whether it saved — the reorder below depends on both writes. */
  async function saveCollab(col: Collab): Promise<boolean> {
    feedback.clear();
    const result = await put(`/api/mediakits/${kitId}/collaborations/${col.id}`, {
      brandName: col.brandName,
      campaign: col.campaign,
      period: col.period,
      resultNote: col.resultNote,
      logoUrl: col.logoUrl,
      displayOrder: col.displayOrder,
    });
    if (!result.ok) feedback.fail(result.message);
    return result.ok;
  }

  /** Swaps two rows' displayOrder; reloads only if both writes landed. */
  async function moveCollab(index: number, dir: -1 | 1) {
    const other = index + dir;
    if (other < 0 || other >= collabs.length) return;
    const a = { ...collabs[index], displayOrder: other };
    const b = { ...collabs[other], displayOrder: index };
    if ((await saveCollab(a)) && (await saveCollab(b))) await load();
  }

  async function deleteCollab(collabId: number) {
    feedback.clear();
    const result = await del(`/api/mediakits/${kitId}/collaborations/${collabId}`);
    if (result.ok) await load();
    else feedback.fail(result.message);
  }

  async function addRate(e: React.FormEvent) {
    e.preventDefault();
    feedback.clear();
    const result = await post(
      `/api/mediakits/${kitId}/ratecard`,
      {
        serviceName: rateForm.serviceName,
        priceAmount: Number(rateForm.priceAmount),
        currency: rateForm.currency,
        note: rateForm.note || null,
        displayOrder: rates.length,
      },
      "Ucret eklenemedi",
      201
    );
    if (result.ok) await load();
    else feedback.fail(result.message);
  }

  async function saveRate(item: RateItem) {
    feedback.clear();
    // Guard the inline edit: an emptied field would coerce to 0 (Number("") === 0)
    // and silently zero the price.
    const price = Number(item.priceAmount);
    if (String(item.priceAmount).trim() === "" || Number.isNaN(price) || price < 0) {
      feedback.fail("Fiyat bos veya gecersiz olamaz.");
      return;
    }
    const result = await put(`/api/mediakits/${kitId}/ratecard/${item.id}`, {
      serviceName: item.serviceName,
      priceAmount: price,
      currency: item.currency,
      note: item.note || null,
      displayOrder: item.displayOrder,
    });
    if (result.ok) feedback.notify("Kaydedildi.");
    else feedback.fail(result.message);
  }

  async function deleteRate(itemId: number) {
    feedback.clear();
    const result = await del(`/api/mediakits/${kitId}/ratecard/${itemId}`);
    if (result.ok) await load();
    else feedback.fail(result.message);
  }

  const patchCollab = (i: number, patch: Partial<Collab>) =>
    setCollabs(collabs.map((x, j) => (j === i ? { ...x, ...patch } : x)));
  const patchRate = (i: number, patch: Partial<RateItem>) =>
    setRates(rates.map((x, j) => (j === i ? { ...x, ...patch } : x)));

  return (
    <div className="grid gap-3">
      <div className="text-sm font-medium">Marka isbirlikleri</div>
      {collabs.map((col, i) => (
        <div key={col.id} className="flex flex-wrap items-center gap-2">
          <Input placeholder="marka *" className="w-32" value={col.brandName}
            onChange={(e) => patchCollab(i, { brandName: e.target.value })} />
          <Input placeholder="kampanya" className="w-40" value={col.campaign ?? ""}
            onChange={(e) => patchCollab(i, { campaign: e.target.value })} />
          <Input placeholder="donem" className="w-24" value={col.period ?? ""}
            onChange={(e) => patchCollab(i, { period: e.target.value })} />
          <Input placeholder="sonuc" className="w-44" value={col.resultNote ?? ""}
            onChange={(e) => patchCollab(i, { resultNote: e.target.value })} />
          <Button size="sm" variant="secondary" onClick={() => saveCollab(col)}>Kaydet</Button>
          <Button size="sm" variant="ghost" onClick={() => moveCollab(i, -1)} disabled={i === 0}>
            <ArrowUp className="h-4 w-4" />
          </Button>
          <Button size="sm" variant="ghost" onClick={() => moveCollab(i, 1)} disabled={i === collabs.length - 1}>
            <ArrowDown className="h-4 w-4" />
          </Button>
          <Button size="sm" variant="ghost" onClick={() => deleteCollab(col.id)}>
            <Trash2 className="h-4 w-4" />
          </Button>
        </div>
      ))}
      <form onSubmit={addCollab} className="flex flex-wrap items-center gap-2 border-t border-line pt-3">
        <Input required placeholder="marka *" className="w-32" value={collabForm.brandName}
          onChange={(e) => setCollabForm({ ...collabForm, brandName: e.target.value })} />
        <Input placeholder="kampanya" className="w-40" value={collabForm.campaign}
          onChange={(e) => setCollabForm({ ...collabForm, campaign: e.target.value })} />
        <Input placeholder="donem" className="w-24" value={collabForm.period}
          onChange={(e) => setCollabForm({ ...collabForm, period: e.target.value })} />
        <Input placeholder="sonuc" className="w-44" value={collabForm.resultNote}
          onChange={(e) => setCollabForm({ ...collabForm, resultNote: e.target.value })} />
        <Button type="submit" size="sm"><Plus className="h-3.5 w-3.5" /> Ekle</Button>
      </form>

      <div className="mt-2 border-t border-line pt-4">
        <div className="mb-2 text-sm font-medium">Calisma ucretleri (rate card)</div>
        {rates.map((r, i) => (
          <div key={r.id} className="mb-2 flex flex-wrap items-center gap-2">
            <Input placeholder="hizmet *" className="w-44" value={r.serviceName}
              onChange={(e) => patchRate(i, { serviceName: e.target.value })} />
            <Input type="number" min={0} placeholder="fiyat *" className="w-28" value={r.priceAmount}
              onChange={(e) => patchRate(i, { priceAmount: e.target.value })} />
            <Select value={r.currency} onChange={(e) => patchRate(i, { currency: e.target.value })}>
              {CURRENCIES.map((c) => <option key={c}>{c}</option>)}
            </Select>
            <Input placeholder="not" className="w-44" value={r.note ?? ""}
              onChange={(e) => patchRate(i, { note: e.target.value })} />
            <Button size="sm" variant="secondary" onClick={() => saveRate(r)}>Kaydet</Button>
            <Button size="sm" variant="ghost" onClick={() => deleteRate(r.id)}>
              <Trash2 className="h-4 w-4" />
            </Button>
          </div>
        ))}
        <form onSubmit={addRate} className="flex flex-wrap items-center gap-2">
          <Input required placeholder="hizmet *" className="w-44" value={rateForm.serviceName}
            onChange={(e) => setRateForm({ ...rateForm, serviceName: e.target.value })} />
          <Input required type="number" min={0} placeholder="fiyat *" className="w-28" value={rateForm.priceAmount}
            onChange={(e) => setRateForm({ ...rateForm, priceAmount: e.target.value })} />
          <Select value={rateForm.currency} onChange={(e) => setRateForm({ ...rateForm, currency: e.target.value })}>
            {CURRENCIES.map((c) => <option key={c}>{c}</option>)}
          </Select>
          <Input placeholder="not" className="w-44" value={rateForm.note}
            onChange={(e) => setRateForm({ ...rateForm, note: e.target.value })} />
          <Button type="submit" size="sm"><Plus className="h-3.5 w-3.5" /> Ekle</Button>
        </form>
      </div>
    </div>
  );
}
