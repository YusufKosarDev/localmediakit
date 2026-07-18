export const metadata = {
  title: "LocalMediaKit",
  description: "Icerik ureticileri icin canli medya kiti platformu",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="tr">
      <body style={{ margin: 0 }}>{children}</body>
    </html>
  );
}
