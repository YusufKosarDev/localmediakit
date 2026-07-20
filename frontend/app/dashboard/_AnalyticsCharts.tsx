"use client";

import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

type Daily = { date: string; views: number; uniqueVisitors: number };
type Entry = { label: string; count: number };

// dataviz-validated hues: brand violet for the single-series trend + magnitude
// bars; a small categorical set (violet / green / magenta) for device slices.
const BRAND = "#6d40e6";
const GRID = "#e8e8ec";
const AXIS = "#9698a1";
const CATEGORICAL = ["#6d40e6", "#008300", "#e87ba4"];

function TooltipBox({
  active,
  payload,
  label,
  suffix,
}: {
  active?: boolean;
  payload?: Array<{ value: number; name: string }>;
  label?: string;
  suffix?: string;
}) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-lg border border-line bg-surface px-2.5 py-1.5 text-xs shadow-md">
      {label && <div className="mb-0.5 font-medium text-fg">{label}</div>}
      {payload.map((p, i) => (
        <div key={i} className="tabular-nums text-muted">
          {p.name}: <span className="font-medium text-fg">{p.value.toLocaleString("tr-TR")}</span>
          {suffix}
        </div>
      ))}
    </div>
  );
}

export function ViewsTrend({ data }: { data: Daily[] }) {
  const short = data.map((d) => ({ ...d, day: d.date.slice(5) }));
  return (
    <ResponsiveContainer width="100%" height={180}>
      <AreaChart data={short} margin={{ top: 6, right: 8, bottom: 0, left: -18 }}>
        <defs>
          <linearGradient id="viewsFill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={BRAND} stopOpacity={0.28} />
            <stop offset="100%" stopColor={BRAND} stopOpacity={0} />
          </linearGradient>
        </defs>
        <CartesianGrid stroke={GRID} strokeDasharray="3 3" vertical={false} />
        <XAxis dataKey="day" tick={{ fill: AXIS, fontSize: 11 }} tickLine={false} axisLine={false} minTickGap={24} />
        <YAxis tick={{ fill: AXIS, fontSize: 11 }} tickLine={false} axisLine={false} width={36} allowDecimals={false} />
        <Tooltip content={<TooltipBox suffix=" goruntulenme" />} cursor={{ stroke: BRAND, strokeOpacity: 0.3 }} />
        <Area type="monotone" dataKey="views" name="Goruntulenme" stroke={BRAND} strokeWidth={2}
          fill="url(#viewsFill)" dot={false} activeDot={{ r: 4 }} />
      </AreaChart>
    </ResponsiveContainer>
  );
}

export function ReferrerBars({ data }: { data: Entry[] }) {
  const top = data.slice(0, 6);
  return (
    <ResponsiveContainer width="100%" height={Math.max(120, top.length * 34)}>
      <BarChart data={top} layout="vertical" margin={{ top: 0, right: 12, bottom: 0, left: 8 }}>
        <XAxis type="number" hide allowDecimals={false} />
        <YAxis type="category" dataKey="label" tick={{ fill: AXIS, fontSize: 11 }} tickLine={false}
          axisLine={false} width={96} />
        <Tooltip content={<TooltipBox />} cursor={{ fill: BRAND, fillOpacity: 0.06 }} />
        <Bar dataKey="count" name="Ziyaret" fill={BRAND} radius={[0, 4, 4, 0]} barSize={16} />
      </BarChart>
    </ResponsiveContainer>
  );
}

export function DeviceBars({ data }: { data: Entry[] }) {
  return (
    <ResponsiveContainer width="100%" height={Math.max(90, data.length * 34)}>
      <BarChart data={data} layout="vertical" margin={{ top: 0, right: 12, bottom: 0, left: 8 }}>
        <XAxis type="number" hide allowDecimals={false} />
        <YAxis type="category" dataKey="label" tick={{ fill: AXIS, fontSize: 11 }} tickLine={false}
          axisLine={false} width={80} />
        <Tooltip content={<TooltipBox />} cursor={{ fill: BRAND, fillOpacity: 0.06 }} />
        <Bar dataKey="count" name="Ziyaret" radius={[0, 4, 4, 0]} barSize={16}>
          {data.map((_, i) => (
            <Cell key={i} fill={CATEGORICAL[i % CATEGORICAL.length]} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}
