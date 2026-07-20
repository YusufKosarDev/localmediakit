"use client";

import { Download } from "lucide-react";

// Client-side PDF export: the browser's own print-to-PDF. Zero backend load,
// zero dependencies. Hidden in the printed output via .no-print, so the exported
// PDF is the clean media kit.
export default function PrintButton() {
  return (
    <div className="no-print mb-2 flex justify-end">
      <button
        onClick={() => window.print()}
        className="inline-flex items-center gap-1.5 rounded-lg border border-line bg-surface px-2.5 py-1.5 text-xs text-muted transition-colors hover:text-fg"
      >
        <Download className="h-3.5 w-3.5" />
        PDF olarak indir
      </button>
    </div>
  );
}
