import type { Metadata } from "next";
import { AuthProvider } from "@/components/auth-provider";
import { ChatWidget } from "@/components/chat-widget";
import { Header } from "@/components/header";
import { UiProvider } from "@/components/ui-provider";
import { Footer } from "@/components/footer";
import { LiquidMotionObserver } from "@/components/liquid-motion";
import "./globals.css";
import "@/styles/tokens.css";
import "@/styles/liquid-glass.css";
import "@/styles/guest-portal.css";
import "@/styles/admin-cockpit.css";

export const metadata: Metadata = {
  title: "HotelAI · Đặt phòng thông minh",
  description: "Tìm kiếm, đặt phòng và quản lý khách sạn tích hợp AI",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="vi">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link
          rel="preconnect"
          href="https://fonts.gstatic.com"
          crossOrigin="anonymous"
        />
        {/* eslint-disable-next-line @next/next/no-page-custom-font */}
        <link
          rel="stylesheet"
          href="https://fonts.googleapis.com/css2?family=Be+Vietnam+Pro:ital,wght@0,300..800;1,400&family=Playfair+Display:ital,wght@0,400..700;1,400..600&family=Plus+Jakarta+Sans:wght@400..800&display=swap"
        />
      </head>
      <body>
        <UiProvider>
          <AuthProvider>
            <LiquidMotionObserver />
            <Header />
            <main>{children}</main>
            <Footer />
            <ChatWidget />
          </AuthProvider>
        </UiProvider>
      </body>
    </html>
  );
}
