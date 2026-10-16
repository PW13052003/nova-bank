"use client";

import { useState } from "react";
import QRCode from "qrcode";
import { apiFetch } from "@/lib/api";

type Phase = "idle" | "setup" | "enabled";

export default function SecurityPage() {
  const [phase, setPhase] = useState<Phase>("idle");
  const [secret, setSecret] = useState("");
  const [qr, setQr] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function startSetup() {
    setError("");
    setLoading(true);
    try {
      const data = await apiFetch("/2fa/setup", { method: "POST", body: "{}" });
      setSecret(data.secret);
      setQr(await QRCode.toDataURL(data.otpAuthUri, { margin: 1, width: 224 }));
      setPassword("");
      setCode("");
      setPhase("setup");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Setup failed";
      if (message.includes("already enabled")) {
        setPhase("enabled");
      } else {
        setError(message);
      }
    } finally {
      setLoading(false);
    }
  }

  async function confirmSetup(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      await apiFetch("/2fa/confirm", {
        method: "POST",
        body: JSON.stringify({ password, code }),
      });
      setSecret("");
      setQr("");
      setPassword("");
      setCode("");
      setPhase("enabled");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Confirmation failed");
      setCode("");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="mx-auto min-h-screen max-w-md px-6 py-12">
      <a href="/dashboard" className="text-sm text-muted">
        ← Back to accounts
      </a>

      <h1 className="mt-8 font-serif text-2xl text-ink">Security</h1>
      <div className="mt-4 mb-8 h-px bg-line" />

      <h2 className="mb-2 text-lg text-ink">Two-factor authentication</h2>

      {phase === "idle" && (
        <>
          <p className="mb-6 text-sm text-muted">
            Add a second step to logging in using an authenticator app such as Google Authenticator or 1Password.
          </p>
          {error && <p className="mb-4 text-sm text-debit">{error}</p>}
          <button
            type="button"
            onClick={startSetup}
            disabled={loading}
            className="w-full bg-ink py-2.5 text-sm font-medium text-paper transition hover:bg-ink/90 disabled:opacity-50"
          >
            {loading ? "Starting..." : "Set up two-factor"}
          </button>
        </>
      )}

      {phase === "setup" && (
        <>
          <p className="mb-6 text-sm text-muted">
            Scan this code with your authenticator app, then enter your password and the 6-digit code it shows.
          </p>

          {qr && (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={qr} alt="Authenticator QR code" width={224} height={224} className="mb-4" />
          )}

          <p className="mb-8 text-sm text-muted">
            Can&apos;t scan? Enter this key manually:{" "}
            <span className="break-all font-mono text-ink">{secret}</span>
          </p>

          <form onSubmit={confirmSetup} className="space-y-6">
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
                className="mt-2 w-full border-0 border-b border-line bg-transparent py-1.5 font-mono tracking-widest text-ink focus:border-ink focus:outline-none"
              />
            </div>

            {error && <p className="text-sm text-debit">{error}</p>}

            <button
              type="submit"
              disabled={loading || code.length !== 6 || password.length === 0}
              className="w-full bg-ink py-2.5 text-sm font-medium text-paper transition hover:bg-ink/90 disabled:opacity-50"
            >
              {loading ? "Confirming..." : "Turn on two-factor"}
            </button>
          </form>
        </>
      )}

      {phase === "enabled" && (
        <p className="border-l-2 border-credit pl-3 text-sm text-credit">
          Two-factor authentication is on. You&apos;ll be asked for a code each time you log in.
        </p>
      )}
    </div>
  );
}