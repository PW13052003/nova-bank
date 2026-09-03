"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { apiFetch } from "@/lib/api";

interface Account {
  id: string;
  accountNumber: string;
  accountType: string;
  status: string;
  balanceCents: number;
}

function formatCents(cents: number) {
  return (cents / 100).toLocaleString("en-US", { style: "currency", currency: "USD" });
}

export default function DashboardPage() {
  const router = useRouter();
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [newAccountType, setNewAccountType] = useState("CHECKING");
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    loadAccounts();
  }, []);

  async function loadAccounts() {
    try {
      const data = await apiFetch("/accounts");
      setAccounts(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load accounts");
    } finally {
      setLoading(false);
    }
  }

  async function handleLogout() {
    await apiFetch("/auth/logout", { method: "POST" });
    router.push("/login");
  }

  async function handleCreateAccount(e: React.FormEvent) {
    e.preventDefault();
    setCreating(true);
    setError("");

    try {
      await apiFetch("/accounts", {
        method: "POST",
        body: JSON.stringify({ accountType: newAccountType }),
      });
      setShowCreateForm(false);
      await loadAccounts();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create account");
    } finally {
      setCreating(false);
    }
  }

  const totalBalance = accounts.reduce((sum, a) => sum + a.balanceCents, 0);

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <p className="text-muted">Loading...</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen px-6 py-10">
      <div className="mx-auto max-w-2xl">
        <div className="flex items-baseline justify-between">
          <a href="/dashboard" className="font-serif text-2xl text-ink">
            Nova Bank
          </a>
          <button
            onClick={handleLogout}
            className="text-sm text-muted hover:text-ink"
          >
            Log out
          </button>
        </div>
        <div className="mt-6 mb-10 h-px bg-line" />

        <div className="mb-10">
          <p className="text-sm text-muted">Total balance</p>
          <p className="mt-1 font-serif text-4xl text-ink">{formatCents(totalBalance)}</p>
        </div>

        <div className="mb-4 flex items-baseline justify-between">
          <h2 className="text-sm text-muted">Accounts</h2>
          <button
            onClick={() => setShowCreateForm(!showCreateForm)}
            className="text-sm text-ink underline underline-offset-2"
          >
            {showCreateForm ? "Cancel" : "+ New account"}
          </button>
        </div>

        {showCreateForm && (
          <form
            onSubmit={handleCreateAccount}
            className="mb-6 flex items-center gap-3 border-b border-line pb-6"
          >
            <select
              value={newAccountType}
              onChange={(e) => setNewAccountType(e.target.value)}
              className="border border-line bg-transparent px-3 py-2 text-sm text-ink"
            >
              <option value="CHECKING">Checking</option>
              <option value="SAVINGS">Savings</option>
            </select>
            <button
              type="submit"
              disabled={creating}
              className="bg-ink px-4 py-2 text-sm font-medium text-paper hover:bg-ink/90 disabled:opacity-50"
            >
              {creating ? "Creating..." : "Create"}
            </button>
          </form>
        )}

        {error && <p className="mb-4 text-sm text-debit">{error}</p>}

        {accounts.length === 0 ? (
          <p className="text-sm text-muted">You don&apos;t have any accounts yet.</p>
        ) : (
          <div className="divide-y divide-line border-t border-line">
            {accounts.map((account) => (
              <a
                key={account.id}
                href={`/accounts/${account.id}`}
                className="flex items-center justify-between py-5 transition hover:bg-ink/[0.02]"
              >
                <div>
                  <p className="text-sm text-ink">{account.accountType}</p>
                  <p className="text-xs text-muted">····{account.accountNumber.slice(-4)}</p>
                </div>
                <p className="text-lg text-ink">{formatCents(account.balanceCents)}</p>
              </a>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}