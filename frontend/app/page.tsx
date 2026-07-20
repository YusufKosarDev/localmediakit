const CARD: React.CSSProperties = {
  border: "1px solid #e5e7eb",
  borderRadius: 12,
  padding: "18px 20px",
  background: "#fff",
};

export default function Home() {
  return (
    <main
      style={{
        minHeight: "100vh",
        background: "#f6f7f9",
        color: "#1a1f27",
        fontFamily: "system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif",
        display: "flex",
        justifyContent: "center",
        padding: "56px 16px",
      }}
    >
      <div style={{ maxWidth: 640, width: "100%" }}>
        <h1 style={{ fontSize: 34, margin: "0 0 8px" }}>LocalMediaKit</h1>
        <p style={{ fontSize: 18, color: "#6b7280", margin: "0 0 28px" }}>
          Icerik ureticileri icin canli medya kiti platformu. Istatistik, etkilesim
          orani, demografi ve marka isbirliklerini tek sayfada, markalarla paylasin.
        </p>

        <div style={{ display: "grid", gap: 12 }}>
          <div style={CARD}>
            <strong>Ornek public medya kiti</strong>
            <p style={{ margin: "4px 0 8px", color: "#6b7280", fontSize: 14 }}>
              Edge&apos;den servis edilen, yayinlanmis bir kit ornegi.
            </p>
            <a href="/demo" style={{ color: "#2563eb", textDecoration: "none", fontWeight: 600 }}>
              /demo sayfasini ac →
            </a>
          </div>

          <div style={CARD}>
            <strong>Panosu kesfet</strong>
            <p style={{ margin: "4px 0 8px", color: "#6b7280", fontSize: 14 }}>
              Editor, analitik, versiyon gecmisi ve PRO ozelliklerini dolu bir demo
              hesabiyla gorun.
            </p>
            <a href="/login" style={{ color: "#2563eb", textDecoration: "none", fontWeight: 600 }}>
              Giris / Demo olarak gez →
            </a>
          </div>

          <div style={{ display: "flex", gap: 16, fontSize: 14 }}>
            <a href="/register" style={{ color: "#2563eb", textDecoration: "none" }}>
              Kayit ol
            </a>
            <a href="/login" style={{ color: "#2563eb", textDecoration: "none" }}>
              Giris yap
            </a>
          </div>
        </div>

        <p style={{ marginTop: 32, fontSize: 12, color: "#9ca3af" }}>
          Egitim / portfolyo projesi. Odeme akisi Stripe test modunda calisir; gercek
          odeme alinmaz.
        </p>
      </div>
    </main>
  );
}
