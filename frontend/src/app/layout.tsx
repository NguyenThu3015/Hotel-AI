import type { Metadata } from "next";
import { AuthProvider } from "@/components/auth-provider";
import { ChatWidget } from "@/components/chat-widget";
import { Header } from "@/components/header";
import { UiProvider } from "@/components/ui-provider";
import { Footer } from "@/components/footer";
import "./globals.css";

export const metadata: Metadata = {
  title: "HotelAI · Đặt phòng thông minh",
  description: "Tìm kiếm, đặt phòng và quản lý khách sạn tích hợp AI",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="vi">
      <body>
        <UiProvider>
          <AuthProvider>
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
