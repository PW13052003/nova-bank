"use client";

import { Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { apiFetch } from "@/lib/api";

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const expired = searchParams.get("expired") === "true";
  const [step, setStep] = useState<"password" | "code">("password");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handlePassword(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      const data = await apiFetch("/auth/login", {
        method: "POST",
        body: JSON.stringify({ email, password }),
      });
      if (data?.twoFactorRequired) {
        setPassword("");
        setCode("");
        setStep("code");
      } else {
        router.push("/dashboard");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed");
    } finally {
      setLoading(false);
    }
  }

  async function handleCode(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      await apiFetch("/auth/2fa/verify", {
        method: "POST",
        body: JSON.stringify({ code }),
      });
      router.push("/dashboard");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Verification failed";
      setError(message);
      setCode("");
      if (message === "Please log in again") {
        setStep("password");
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-6">
      <div className="w-full max-w-sm">
        <a href="/" className="font-serif text-2xl text-ink">
          Nova Bank
        </a>
        <div className="mt-6 mb-8 h-px bg-line" />

        {step === "password" ? (
          <>
            <h1 className="mb-6 text-lg text-ink">Log in to your account</h1>

            {expired && (
              <p className="mb-6 border-l-2 border-debit pl-3 text-sm text-debit">
                Your session expired. Please log in again.
              </p>
            )}

            <form onSubmit={handlePassword} className="space-y-6">
              <div>
                <label className="block text-sm text-muted">Email</label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  className="mt-2 w-full border-0 border-b border-line bg-transparent py-1.5 text-ink focus:border-ink focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-sm text-muted">Password</label>
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  className="mt-2 w-full border-0 border-b border-line bg-transparent py-1.5 text-ink focus:border-ink focus:outline-none"
                />
              </div>

              {error && <p className="text-sm text-debit">{error}</p>}

              <button
                type="submit"
                disabled={loading}
                className="w-full bg-ink py-2.5 text-sm font-medium text-paper transition hover:bg-ink/90 disabled:opacity-50"
              >
                {loading ? "Logging in..." : "Log in"}
              </button>
            </form>

            <p className="mt-8 text-sm text-muted">
              Don&apos;t have an account?{" "}
              <a href="/signup" className="text-ink underline underline-offset-2">
                Sign up
              </a>
            </p>
          </>
        ) : (
          <>
            <h1 className="mb-2 text-lg text-ink">Two-factor authentication</h1>
            <p className="mb-6 text-sm text-muted">
              Enter the 6-digit code from your authenticator app.
            </p>

            <form onSubmit={handleCode} className="space-y-6">
              <div>
                <label className="block text-sm text-muted">Code</label>
                <input
                  type="text"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  pattern="[0-9]{6}"
                  maxLength={6}
                  value={code}
                  onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
                  required
                  autoFocus
                  className="mt-2 w-full border-0 border-b border-line bg-transparent py-1.5 font-mono tracking-widest text-ink focus:border-ink focus:outline-none"
                />
              </div>

              {error && <p className="text-sm text-debit">{error}</p>}

              <button
                type="submit"
                disabled={loading || code.length !== 6}
                className="w-full bg-ink py-2.5 text-sm font-medium text-paper transition hover:bg-ink/90 disabled:opacity-50"
              >
                {loading ? "Verifying..." : "Verify"}
              </button>
            </form>

            <button
              type="button"
              onClick={() => {
                setError("");
                setStep("password");
              }}
              className="mt-8 text-sm text-muted underline underline-offset-2"
            >
              Back to log in
            </button>
          </>
        )}
      </div>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={<div className="flex min-h-screen items-center justify-center"><p className="text-muted">Loading...</p></div>}>
      <LoginForm />
    </Suspense>
  );
}