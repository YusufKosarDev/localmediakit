export default function RootNotFound() {
  return (
    <main
      style={{
        minHeight: "100vh",
        background: "#f6f7f9",
        color: "#1a1f27",
        fontFamily: "system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: "48px 16px",
        textAlign: "center",
      }}
    >
      <div>
        <p style={{ fontSize: 56, margin: 0 }} aria-hidden>
          🔍
        </p>
        <h1 style={{ fontSize: 24, margin: "16px 0 8px" }}>Sayfa bulunamadi</h1>
        <p style={{ color: "#6b7280", maxWidth: 380, margin: "0 auto" }}>
          Aradiginiz sayfa mevcut degil ya da tasinmis olabilir.
        </p>
        <p style={{ marginTop: 24 }}>
          <a href="/" style={{ color: "#2563eb", textDecoration: "none" }}>
            Ana sayfaya don →
          </a>
        </p>
      </div>
    </main>
  );
}
