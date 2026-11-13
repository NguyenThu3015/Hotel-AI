"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { AppIcon } from "./app-icon";

export function Footer() {
  const pathname = usePathname();
  if (pathname.startsWith("/admin")) return null;

  return (
    <footer className="site-footer luxury-site-footer">
      <div className="footer-prestige-ribbon">
        <div className="footer-prestige-item">
          <AppIcon name="diamond" />
          <span>MICHELIN Guide · Two Keys Selection 2026</span>
        </div>
        <div className="footer-prestige-item">
          <AppIcon name="crown" />
          <span>Forbes Travel Guide · Five-Star Verified</span>
        </div>
        <div className="footer-prestige-item">
          <AppIcon name="award" />
          <span>World Luxury Hotel Awards · Asia Winner</span>
        </div>
        <div className="footer-prestige-item">
          <AppIcon name="globe" />
          <span>10 Điểm Đến Biểu Tượng Việt Nam</span>
        </div>
      </div>
      <div className="footer-brand">
        <Link href="/" className="brand" aria-label="HotelAI - Trang chủ">
          <span className="brand-mark">H</span>
          <span className="brand-copy">
            <b>
              Hotel<span>AI</span>
            </b>
            <small>Grand Luxury Collection</small>
          </span>
        </Link>
        <p>
          Tuyệt tác nghỉ dưỡng giao hòa giữa di sản kiến trúc bản địa, nghệ
          thuật hiếu khách 5 sao và trí tuệ nhân tạo cá nhân hóa từng khoảnh
          khắc lưu trú.
        </p>
        <span className="footer-status">
          <i /> Hệ thống đặt phòng &amp; Quản gia AI trực tuyến 24/7
        </span>
      </div>
      <div>
        <strong>Bộ sưu tập</strong>
        <Link href="/">Tìm phòng &amp; Biệt thự</Link>
        <Link href="/rooms">Danh mục Suite thượng hạng</Link>
        <Link href="/bookings">Hành trình kỳ nghỉ của tôi</Link>
      </div>
      <div>
        <strong>Đặc quyền hội viên</strong>
        <Link href="/profile">Hồ sơ &amp; Đặc quyền cá nhân</Link>
        <Link href="/#search">Kiểm tra phòng trống tức thì</Link>
        <span>Đón tiễn sân bay &amp; Butler riêng 24/7</span>
      </div>
      <div className="footer-contact">
        <strong>Quản gia &amp; Lễ tân VIP</strong>
        <span>
          <AppIcon name="phone" /> Concierge: 1800 6886 (Miễn phí 24/7)
        </span>
        <span>
          <AppIcon name="shield" /> Bảo chứng giá cuối &amp; Khóa phòng tức thì
        </span>
        <Link className="footer-action" href="/#search">
          Khởi tạo kỳ nghỉ thượng lưu <AppIcon name="arrow" />
        </Link>
      </div>
      <div className="footer-bottom">
        <span>© 2026 HotelAI Grand Luxury Collection · All Rights Reserved</span>
        <span>Chuỗi khu nghỉ dưỡng &amp; khách sạn 5 sao tích hợp AI đầu tiên tại Việt Nam</span>
      </div>
    </footer>
  );
}
