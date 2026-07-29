import type { Metadata, Viewport } from "next";
import { GeistSans } from "geist/font/sans";
import "./globals.css";

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
  // Installability metadata. The manifest is linked site-wide because the
  // browser reads it before deciding anything, but the service worker that
  // backs it is registered only from the signed-in surfaces.
  manifest: "/manifest.webmanifest",
  appleWebApp: {
    capable: true,
    title: "LocalMediaKit",
    statusBarStyle: "default",
  },
  icons: {
    apple: "/apple-touch-icon.png",
  },
};

export const viewport: Viewport = {
  themeColor: "#6d40e6",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="tr" className={GeistSans.variable}>
      <body className="min-h-screen bg-page text-fg font-sans antialiased">
        {children}
      </body>
    </html>
  );
}
