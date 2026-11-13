import type { Tokens } from "./types";
import { viApiError } from "./vi";

const API_URL =
  process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8081/api/v1";

export class ApiRequestError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string,
  ) {
    super(message);
    this.name = "ApiRequestError";
  }
}

class ApiClient {
  private accessToken: string | null = null;
  private refreshToken: string | null = null;
  private refreshPromise: Promise<boolean> | null = null;
  private hydrated = false;
  private readonly sessionClearedListeners = new Set<() => void>();

  hydrate() {
    if (typeof window === "undefined" || this.hydrated) return;
    this.accessToken = localStorage.getItem("accessToken");
    this.refreshToken = localStorage.getItem("refreshToken");
    this.hydrated = true;
  }

  hasSession() {
    this.hydrate();
    return Boolean(this.accessToken || this.refreshToken);
  }

  onSessionCleared(listener: () => void) {
    this.sessionClearedListeners.add(listener);
    return () => this.sessionClearedListeners.delete(listener);
  }

  setTokens(tokens: Tokens | null) {
    this.hydrate();
    const hadSession = Boolean(this.accessToken || this.refreshToken);
    this.accessToken = tokens?.accessToken ?? null;
    this.refreshToken = tokens?.refreshToken ?? null;
    if (typeof window !== "undefined") {
      if (tokens) {
        localStorage.setItem("accessToken", tokens.accessToken);
        localStorage.setItem("refreshToken", tokens.refreshToken);
      } else {
        localStorage.removeItem("accessToken");
        localStorage.removeItem("refreshToken");
      }
    }
    if (!tokens && hadSession) {
      this.sessionClearedListeners.forEach((listener) => listener());
    }
  }

  async logout() {
    if (this.refreshToken) {
      await this.post("/auth/logout", {
        refreshToken: this.refreshToken,
      }).catch(() => undefined);
    }
    this.setTokens(null);
  }

  async request<T>(
    path: string,
    init: RequestInit = {},
    retry = true,
    authenticated = true,
  ): Promise<T> {
    this.hydrate();
    const headers = new Headers(init.headers);
    if (init.body && !headers.has("Content-Type"))
      headers.set("Content-Type", "application/json");
    if (authenticated && this.accessToken)
      headers.set("Authorization", `Bearer ${this.accessToken}`);
    const response = await fetch(`${API_URL}${path}`, {
      ...init,
      headers,
      cache: "no-store",
    });
    if (response.status === 401 && authenticated) {
      if (retry && this.refreshToken && (await this.refreshAccessToken())) {
        return this.request(path, init, false, true);
      }
      this.setTokens(null);
    }
    if (!response.ok) {
      const error = await response
        .json()
        .catch(() => ({ detail: "Không thể kết nối máy chủ" }));
      const correlationId =
        response.headers.get("X-Correlation-Id") ?? error.correlationId;
      const problemCode =
        error.code ??
        (typeof error.type === "string"
          ? error.type.split("/").filter(Boolean).at(-1)
          : undefined);
      throw new ApiRequestError(
        viApiError(error, response.status, correlationId),
        response.status,
        problemCode,
      );
    }
    if (
      response.status === 204 ||
      response.headers.get("content-length") === "0"
    )
      return undefined as T;
    return response.json() as Promise<T>;
  }

  get<T>(path: string) {
    return this.request<T>(path);
  }

  getPublic<T>(path: string) {
    return this.request<T>(path, {}, false, false);
  }

  post<T>(path: string, body?: unknown, headers?: HeadersInit) {
    return this.request<T>(path, {
      method: "POST",
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  }

  postPublic<T>(path: string, body?: unknown, headers?: HeadersInit) {
    return this.request<T>(
      path,
      {
        method: "POST",
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
      },
      false,
      false,
    );
  }

  put<T>(path: string, body: unknown, headers?: HeadersInit) {
    return this.request<T>(path, {
      method: "PUT",
      headers,
      body: JSON.stringify(body),
    });
  }

  patch<T>(path: string, body: unknown, headers?: HeadersInit) {
    return this.request<T>(path, {
      method: "PATCH",
      headers,
      body: JSON.stringify(body),
    });
  }

  delete<T>(path: string) {
    return this.request<T>(path, { method: "DELETE" });
  }

  async download(path: string, retry = true): Promise<Blob> {
    this.hydrate();
    const headers = new Headers();
    if (this.accessToken)
      headers.set("Authorization", `Bearer ${this.accessToken}`);
    const response = await fetch(`${API_URL}${path}`, {
      headers,
      cache: "no-store",
    });
    if (response.status === 401) {
      if (retry && this.refreshToken && (await this.refreshAccessToken()))
        return this.download(path, false);
      this.setTokens(null);
    }
    if (!response.ok) {
      const error = await response
        .json()
        .catch(() => ({ detail: "Không thể tải tệp" }));
      throw new Error(viApiError(error, response.status));
    }
    return response.blob();
  }

  private refreshAccessToken(): Promise<boolean> {
    if (this.refreshPromise) return this.refreshPromise;

    this.refreshPromise = this.performRefresh().finally(() => {
      this.refreshPromise = null;
    });
    return this.refreshPromise;
  }

  private async performRefresh(): Promise<boolean> {
    if (!this.refreshToken) return false;
    try {
      const refreshed = await fetch(`${API_URL}/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken: this.refreshToken }),
        cache: "no-store",
      });
      if (!refreshed.ok) return false;

      this.setTokens((await refreshed.json()) as Tokens);
      return true;
    } catch {
      return false;
    }
  }
}

export const api = new ApiClient();
