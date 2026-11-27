"use client";

import { useAuth } from "@/components/auth-provider";
import { AppIcon } from "@/components/app-icon";
import { api } from "@/lib/api";
import type { AuthChallenge } from "@/lib/types";
import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useFlashNotifications } from "@/components/ui-provider";

export default function LoginPage() {
  const auth = useAuth();
  const router = useRouter();
  const [registering, setRegistering] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [challenge, setChallenge] = useState<AuthChallenge | null>(null);
  const [otp, setOtp] = useState("");
  const [form, setForm] = useState({
    email: "",
    password: "",
    fullName: "",
    phone: "",
  });
  const [error, setError] = useState("");
  useFlashNotifications(error);
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      let authenticatedRoles: string[] = [];
      if (resetting) {
        if (!challenge) {
          const next = await api.post<AuthChallenge>("/auth/forgot-password", {
            email: form.email,
          });
          setChallenge(next);
          return;
        }
        await api.post("/auth/reset-password", {
          challengeId: challenge.challengeId,
          code: otp,
          newPassword: form.password,
        });
        setResetting(false);
        setChallenge(null);
        setOtp("");
        return;
      }
      if (registering) {
        if (!challenge) {
          setChallenge(await auth.register(form));
          return;
        }
        await auth.verifyRegistration(challenge.challengeId, otp);
      } else {
        const profile = await auth.login(form.email, form.password);
        authenticatedRoles = profile.roles;
      }
      const requestedPath = new URLSearchParams(window.location.search).get(
        "next",
      );
      router.push(
        authenticatedRoles.some((role) =>
          ["ADMIN", "MANAGER", "RECEPTIONIST", "HOUSEKEEPER"].includes(role),
        )
          ? "/admin"
          : requestedPath?.startsWith("/") && !requestedPath.startsWith("//")
            ? requestedPath
            : "/",
      );
    } catch (reason) {
      setError((reason as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="auth-page">
      <aside className="auth-story" data-glass-spotlight="">
        <Link href="/" className="auth-brand">
          Hotel<span>AI</span>
        </Link>
        <div>
          <p className="eyebrow">KỲ NGHỈ BẮT ĐẦU TỪ ĐÂY</p>
          <h2>Dễ dàng quản lý mọi hành trình của bạn.</h2>
          <p>
            Lưu thông tin cá nhân, theo dõi thanh toán VietQR, đổi lịch linh hoạt
            và kết nối Quản gia AI xuyên suốt 10 điểm đến.
          </p>
          <ul>
            <li>
              <b>
                <AppIcon name="shield" />
              </b>{" "}
              Thông tin &amp; phiên đăng nhập được bảo vệ chuẩn JWT
            </li>
            <li>
              <b>
                <AppIcon name="check" />
              </b>{" "}
              Xác minh OTP 6 số tức thì qua Email / Zalo
            </li>
            <li>
              <b>
                <AppIcon name="sparkles" />
              </b>{" "}
              Đồng bộ đặc quyền hội viên &amp; Quản gia AI 24/7
            </li>
          </ul>
        </div>
      </aside>
      <div className="auth-card" data-glass-spotlight="">
        <p className="eyebrow">TÀI KHOẢN HOTELAI</p>
        <h1>
          {resetting
            ? "Khôi phục mật khẩu"
            : registering
              ? "Tạo tài khoản"
              : "Chào mừng trở lại"}
        </h1>
        <p className="auth-subtitle">
          Quản lý hành trình và nhận đề xuất cá nhân hóa phù hợp với bạn.
        </p>
        <form onSubmit={submit}>
          {registering && !resetting && !challenge && (
            <>
              <label>
                Họ và tên
                <input
                  required
                  value={form.fullName}
                  onChange={(e) =>
                    setForm({ ...form, fullName: e.target.value })
                  }
                />
              </label>
              <label>
                Số điện thoại
                <input
                  value={form.phone}
                  onChange={(e) => setForm({ ...form, phone: e.target.value })}
                />
              </label>
            </>
          )}
          <label>
            Email
            <input
              type="email"
              required
              disabled={Boolean(challenge)}
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
            />
          </label>
          {challenge && (
            <>
              <p className="muted">
                Mã xác minh đã gửi tới {challenge.maskedDestination} và hết hạn
                lúc {new Date(challenge.expiresAt).toLocaleTimeString("vi-VN")}.
              </p>
              <label>
                Mã xác minh gồm 6 số
                <input
                  inputMode="numeric"
                  pattern="[0-9]{6}"
                  maxLength={6}
                  required
                  value={otp}
                  onChange={(e) => setOtp(e.target.value)}
                />
              </label>
            </>
          )}
          <label>
            {resetting ? "Mật khẩu mới" : "Mật khẩu"}
            <input
              type="password"
              minLength={10}
              required={!challenge || resetting}
              disabled={registering && Boolean(challenge)}
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
            />
          </label>
          <button className="primary" disabled={busy}>
            {busy
              ? "Đang xử lý..."
              : resetting && !challenge
                ? "Gửi mã xác minh"
                : resetting
                  ? "Đổi mật khẩu"
                  : registering
                    ? challenge
                      ? "Xác minh và đăng nhập"
                      : "Gửi mã xác minh"
                    : "Đăng nhập"}
          </button>
        </form>
        <button
          type="button"
          className="text-action"
          onClick={() => {
            setRegistering(!registering);
            setResetting(false);
            setChallenge(null);
            setOtp("");
          }}
        >
          {registering
            ? "Đã có tài khoản? Đăng nhập"
            : "Chưa có tài khoản? Đăng ký"}
        </button>
        {!registering && (
          <button
            type="button"
            className="text-action secondary-action"
            onClick={() => {
              setResetting(!resetting);
              setChallenge(null);
              setOtp("");
            }}
          >
            {resetting ? "Quay lại đăng nhập" : "Quên mật khẩu?"}
          </button>
        )}
      </div>
    </section>
  );
}
