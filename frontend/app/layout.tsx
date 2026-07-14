export const metadata = {
  title: "LocalMediaKit",
  description: "Canli medya kiti - Adim 0 mimari kaniti",
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
