"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { apiFetch } from "@/lib/api";

export default function SignupPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fullName, setFullName] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      await apiFetch("/auth/signup", {
        method: "POST",
        body: JSON.stringify({ email, password, fullName }),
      });
      router.push("/dashboard");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Signup failed");
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

        <h1 className="mb-6 text-lg text-ink">Create your account</h1>

        <form onSubmit={handleSubmit} className="space-y-6">
          <div>
            <label className="block text-sm text-muted">Full name</label>
            <input
              type="text"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              required
              className="mt-2 w-full border-0 border-b border-line bg-transparent py-1.5 text-ink focus:border-ink focus:outline-none"
            />
          </div>

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
            {loading ? "Creating account..." : "Sign up"}
          </button>
        </form>

        <p className="mt-8 text-sm text-muted">
          Already have an account?{" "}
          <a href="/login" className="text-ink underline underline-offset-2">
            Log in
          </a>
        </p>
      </div>
    </div>
  );
}