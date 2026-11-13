"use client";

import { useAuth } from "@/components/auth-provider";
import { api } from "@/lib/api";
import type { PageResponse } from "@/lib/types";
import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useFlashNotifications } from "@/components/ui-provider";

interface NotificationItem {
  id: string;
  subject?: string;
  content: string;
  readAt?: string;
  createdAt: string;
}

export default function ProfilePage() {
  const auth = useAuth();
  const router = useRouter();
  const [name, setName] = useState(auth.user?.fullName ?? "");
  const [phone, setPhone] = useState(auth.user?.phone ?? "");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  useFlashNotifications(error, message);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [passwords, setPasswords] = useState({
    currentPassword: "",
    newPassword: "",
    confirmPassword: "",
  });
  const [changingPassword, setChangingPassword] = useState(false);

  useEffect(() => {
    if (!auth.user) return;
    const timer = window.setTimeout(() => {
      setName(auth.user?.fullName ?? "");
      setPhone(auth.user?.phone ?? "");
      void api
        .get<PageResponse<NotificationItem>>("/notifications")
        .then((page) => setNotifications(page.content))
        .catch(() => undefined);
    }, 0);
    return () => window.clearTimeout(timer);
  }, [auth.user]);

  if (auth.loading)
    return <section className="content-section">Đang tải hồ sơ…</section>;
  if (!auth.user)
    return (
      <section className="content-section">
        Vui lòng đăng nhập để xem hồ sơ.
      </section>
    );

  async function save(event: FormEvent) {
    event.preventDefault();
    setError("");
    try {
      await api.put("/me", { fullName: name, phone });
      await auth.refreshProfile();
      setMessage("Đã cập nhật hồ sơ.");
    } catch (reason) {
      setError((reason as Error).message);
    }
  }

  async function changePassword(event: FormEvent) {
    event.preventDefault();
    setError("");
    setMessage("");
    if (passwords.newPassword !== passwords.confirmPassword) {
      setError("Mật khẩu xác nhận chưa trùng khớp.");
      return;
    }
    if (passwords.newPassword.length < 10) {
      setError("Mật khẩu mới cần có ít nhất 10 ký tự.");
      return;
    }
    setChangingPassword(true);
    try {
      await api.post("/auth/change-password", {
        currentPassword: passwords.currentPassword,
        newPassword: passwords.newPassword,
      });
      await auth.logout();
      router.replace("/login?passwordChanged=1");
    } catch (reason) {
      setError((reason as Error).message);
    } finally {
      setChangingPassword(false);
    }
  }

  return (
    <section className="content-section narrow-section">
      <div className="section-heading">
        <div>
          <span>TÀI KHOẢN</span>
          <h1>Hồ sơ cá nhân</h1>
        </div>
      </div>
      <div className="profile-grid">
        <form className="panel form-grid" onSubmit={save}>
          <h2>Thông tin liên hệ</h2>
          <label>
            Email
            <input value={auth.user.email} disabled />
          </label>
          <label>
            Họ và tên
            <input
              required
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
          </label>
          <label>
            Điện thoại
            <input
              value={phone}
              onChange={(event) => setPhone(event.target.value)}
            />
          </label>
          <button className="primary">Lưu thay đổi</button>
        </form>
        <section className="panel form-grid">
          <h2>Xác minh email</h2>
          <p className="muted">
            Trạng thái:{" "}
            <strong>
              {auth.user.emailVerified ? "Đã xác minh" : "Chưa xác minh"}
            </strong>
          </p>
          <p className="muted">
            Email được xác minh bằng OTP trong lúc đăng ký. Mọi phiên đăng nhập đều gắn với
            tài khoản đã kích hoạt.
          </p>
        </section>
        <form className="panel form-grid password-panel" onSubmit={changePassword}>
          <div>
            <span className="panel-kicker">BẢO MẬT</span>
            <h2>Đổi mật khẩu</h2>
            <p className="muted">
              Sau khi đổi, hệ thống sẽ đăng xuất các thiết bị để bảo vệ tài khoản.
            </p>
          </div>
          <label>
            Mật khẩu hiện tại
            <input
              type="password"
              autoComplete="current-password"
              required
              value={passwords.currentPassword}
              onChange={(event) =>
                setPasswords({ ...passwords, currentPassword: event.target.value })
              }
            />
          </label>
          <label>
            Mật khẩu mới
            <input
              type="password"
              autoComplete="new-password"
              minLength={10}
              required
              value={passwords.newPassword}
              onChange={(event) =>
                setPasswords({ ...passwords, newPassword: event.target.value })
              }
            />
          </label>
          <label>
            Nhập lại mật khẩu mới
            <input
              type="password"
              autoComplete="new-password"
              minLength={10}
              required
              value={passwords.confirmPassword}
              onChange={(event) =>
                setPasswords({ ...passwords, confirmPassword: event.target.value })
              }
            />
          </label>
          <button className="primary" disabled={changingPassword}>
            {changingPassword ? "Đang cập nhật…" : "Đổi mật khẩu"}
          </button>
        </form>
      </div>
      <section className="panel notification-panel">
        <div className="panel-heading">
          <div>
            <span>THÔNG BÁO</span>
            <h2>Cập nhật hành trình</h2>
          </div>
          <b>{notifications.filter((item) => !item.readAt).length} chưa đọc</b>
        </div>
        <div className="simple-list">
          {notifications.map((item) => (
            <button
              className={item.readAt ? "notification read" : "notification"}
              key={item.id}
              onClick={() => {
                void api
                  .patch(`/notifications/${item.id}/read`, {})
                  .then(() =>
                    setNotifications((values) =>
                      values.map((value) =>
                        value.id === item.id
                          ? { ...value, readAt: new Date().toISOString() }
                          : value,
                      ),
                    ),
                  );
              }}
            >
              <strong>{item.subject ?? "Thông báo"}</strong>
              <span>{item.content}</span>
            </button>
          ))}
          {!notifications.length && <p className="muted">Chưa có thông báo.</p>}
        </div>
      </section>
    </section>
  );
}
