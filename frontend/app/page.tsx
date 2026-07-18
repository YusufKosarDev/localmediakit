export default function Home() {
  return (
    <main style={{ fontFamily: "system-ui, sans-serif", padding: 40 }}>
      <h1>LocalMediaKit</h1>
      <p>Icerik ureticileri icin canli medya kiti platformu.</p>
      <ul>
        <li>
          Ornek public medya kiti: <a href="/demo">/demo</a>
        </li>
        <li>
          <a href="/login">Giris yap</a> · <a href="/register">Kayit ol</a> ·{" "}
          <a href="/dashboard">Dashboard</a>
        </li>
      </ul>
    </main>
  );
}
