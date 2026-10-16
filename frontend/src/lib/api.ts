const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "/api";

export async function apiFetch(path: string, options: RequestInit = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...options.headers,
    },
  });

  const data = await response.json().catch(() => null);

  const isAuthCall = path.startsWith("/auth/");

  if ((response.status === 401 || response.status === 403) && !isAuthCall) {
    if (typeof window !== "undefined" && window.location.pathname !== "/login") {
      window.location.href = "/login?expired=true";
    }
    throw new Error("Session expired");
  }

  if (!response.ok) {
    throw new Error(data?.error || "Request failed");
  }

  return data;
}