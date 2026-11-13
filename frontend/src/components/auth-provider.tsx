"use client";

import { api } from "@/lib/api";
import type { AuthChallenge, Tokens, UserProfile } from "@/lib/types";
import { createContext, useContext, useEffect, useState } from "react";

interface AuthContextValue {
  user: UserProfile | null;
  loading: boolean;
  login(email: string, password: string): Promise<UserProfile>;
  register(data: {
    email: string;
    password: string;
    fullName: string;
    phone?: string;
  }): Promise<AuthChallenge>;
  verifyRegistration(challengeId: string, code: string): Promise<void>;
  logout(): Promise<void>;
  refreshProfile(): Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);

  async function loadProfile(): Promise<UserProfile | null> {
    let profile: UserProfile | null = null;
    try {
      // Identity Service là nguồn hồ sơ phiên cho mọi loại tài khoản.
      // Customer Service /me chỉ tồn tại với tài khoản khách hàng.
      const identityProfile = await api.get<UserProfile>("/auth/me");
      profile = identityProfile;
      if (identityProfile.roles.includes("CUSTOMER")) {
        try {
          const customerProfile = await api.get<UserProfile>("/me");
          profile = {
            ...identityProfile,
            ...customerProfile,
            roles: identityProfile.roles,
          };
        } catch {
          // Projection khách hàng có thể chưa sẵn sàng ngay sau đăng ký.
          // Chỉ giữ hồ sơ Identity nếu phiên vẫn còn hợp lệ.
          if (!api.hasSession()) profile = null;
        }
      }
      setUser(profile);
    } catch {
      setUser(null);
      profile = null;
    } finally {
      setLoading(false);
    }
    return profile;
  }

  useEffect(() => {
    const unsubscribe = api.onSessionCleared(() => {
      setUser(null);
      setLoading(false);
    });
    api.hydrate();
    if (!api.hasSession()) {
      const timer = window.setTimeout(() => setLoading(false), 0);
      return () => {
        window.clearTimeout(timer);
        unsubscribe();
      };
    }
    const timer = window.setTimeout(() => void loadProfile(), 0);
    return () => {
      window.clearTimeout(timer);
      unsubscribe();
    };
  }, []);

  async function authenticate(path: string, payload: unknown) {
    const tokens = await api.post<Tokens>(path, payload);
    api.setTokens(tokens);
    const profile = await loadProfile();
    if (!profile) throw new Error("Không thể tải hồ sơ tài khoản");
    return profile;
  }

  const value: AuthContextValue = {
    user,
    loading,
    login: (email, password) =>
      authenticate("/auth/login", { email, password }),
    register: (data) =>
      api.post<AuthChallenge>("/auth/register", data, {
        "Idempotency-Key": crypto.randomUUID(),
      }),
    verifyRegistration: async (challengeId, code) => {
      await authenticate("/auth/register/verify", { challengeId, code });
    },
    logout: async () => {
      await api.logout();
      setUser(null);
    },
    refreshProfile: async () => {
      await loadProfile();
    },
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside AuthProvider");
  return context;
}
