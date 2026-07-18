"use client";

// Renders when the page could not be generated (e.g. the slug is not cached
// yet and the backend is unreachable). The failed render is not cached, so
// "Tekrar dene" or a later visit will retry cleanly.
export default function KitError({ reset }: { error: Error; reset: () => void }) {
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
          ⏳
        </p>
        <h1 style={{ fontSize: 24, margin: "16px 0 8px" }}>
          Sayfa su anda goruntulenemiyor
        </h1>
        <p style={{ color: "#6b7280", maxWidth: 400, margin: "0 auto" }}>
          Gecici bir sorun olustu. Birkac saniye icinde tekrar deneyin.
        </p>
        <p style={{ marginTop: 24 }}>
          <button
            onClick={() => reset()}
            style={{
              padding: "10px 20px",
              borderRadius: 8,
              border: "1px solid #d1d5db",
              background: "#ffffff",
              cursor: "pointer",
              fontSize: 15,
            }}
          >
            Tekrar dene
          </button>
        </p>
      </div>
    </main>
  );
}
