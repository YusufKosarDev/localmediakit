"use client";

// Client-side PDF export: the browser's own print-to-PDF. Zero backend load,
// zero dependencies. Hidden in the printed output via the no-print class, so
// the exported PDF is the clean media kit (FREE prints keep the badge that is
// already in the footer; PRO prints have none).
export default function PrintButton({ dark }: { dark: boolean }) {
  return (
    <button
      className="no-print"
      onClick={() => window.print()}
      style={{
        position: "absolute",
        top: 16,
        right: 16,
        padding: "6px 12px",
        fontSize: 13,
        borderRadius: 8,
        cursor: "pointer",
        border: `1px solid ${dark ? "#30363d" : "#d1d5db"}`,
        background: dark ? "#21262d" : "#ffffff",
        color: dark ? "#e6edf3" : "#1a1f27",
      }}
    >
      PDF olarak indir
    </button>
  );
}
