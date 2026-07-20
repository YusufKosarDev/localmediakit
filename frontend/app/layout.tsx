import type { Metadata } from "next";

const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL ?? "https://localmediakit.vercel.app";

export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: {
    default: "LocalMediaKit",
    template: "%s · LocalMediaKit",
  },
  description: "Icerik ureticileri icin canli medya kiti platformu.",
  applicationName: "LocalMediaKit",
  openGraph: {
    siteName: "LocalMediaKit",
    type: "website",
    locale: "tr_TR",
  },
  twitter: {
    card: "summary",
  },
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
