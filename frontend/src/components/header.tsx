"use client";

import { useAuth } from "./auth-provider";
import { AppIcon } from "./app-icon";
import { isHotelStaff } from "@/lib/admin-permissions";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";

export function Header() {
  const [open, setOpen] = useState(false);
  const pathname = usePathname();
  const { user, logout } = useAuth();
  const isStaff = isHotelStaff(user?.roles);
  const isCustomer = user?.roles.some((role) => role.toUpperCase() === "CUSTOMER") ?? false;
  const isAdmin = pathname.startsWith("/admin");

  return (
    <header className={`site-header ${isAdmin ? "admin-header" : "luxury-public-header"}`}>
      <div className="header-brand-cluster">
        <Link href="/" className="brand" aria-label="HotelAI - Trang chủ">
          <span className="brand-mark" aria-hidden="true">
            <span className="brand-mark-monogram">H</span>
          </span>
          <span className="brand-copy">
            <b>Hotel<span>AI</span></b>
            <small>Grand Luxury Collection</small>
          </span>
        </Link>
        {!isAdmin && (
          <span className="header-prestige-pill" aria-hidden="true">
            <AppIcon name="diamond" />
            <span>MICHELIN Key · 5★ Sanctuary</span>
          </span>
        )}
      </div>
      <button
        className="menu-button"
        aria-label={open ? "Đóng trình đơn" : "Mở trình đơn"}
        aria-expanded={open}
        onClick={() => setOpen(!open)}
      >
        <AppIcon name={open ? "x" : "menu"} />
      </button>
      <nav className={open ? "nav-open" : ""} onClick={() => setOpen(false)} aria-label="Điều hướng chính">
        <Link className={pathname === "/" ? "active" : ""} href="/">
          <AppIcon name="search" /> <span>Tìm phòng</span>
        </Link>
        <Link className={pathname === "/rooms" ? "active" : ""} href="/rooms">
          <AppIcon name="bed" /> <span>Hạng phòng</span>
        </Link>
        {isCustomer && <Link className={pathname === "/bookings" ? "active" : ""} href="/bookings"><AppIcon name="calendar" /><span>Kỳ nghỉ của tôi</span></Link>}
        {user && <Link className={pathname === "/profile" ? "active" : ""} href="/profile"><AppIcon name="user" /><span>Tài khoản</span></Link>}
        {isStaff && <Link className={pathname === "/admin" ? "active" : ""} href="/admin"><AppIcon name="building" /><span>Điều hành</span></Link>}
        {user ? (
          <div className="header-account">
            <span className="avatar" aria-hidden="true">{user.fullName.trim().charAt(0).toUpperCase()}</span>
            <span className="account-name"><small>Xin chào</small><b>{user.fullName}</b></span>
            <button className="link-button logout-button" onClick={() => void logout()} aria-label="Đăng xuất">
              <AppIcon name="log-out" /> <span>Đăng xuất</span>
            </button>
          </div>
        ) : (
          <Link className="header-login" href="/login">Đăng nhập <AppIcon name="arrow" /></Link>
        )}
      </nav>
    </header>
  );
}
