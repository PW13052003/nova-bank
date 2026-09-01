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

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50">
        <p className="text-gray-500">Loading...</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 px-6 py-10">
      <div className="mx-auto max-w-2xl">
        <div className="mb-8 flex items-center justify-between">
          <h1 className="text-2xl font-semibold text-gray-900">Your accounts</h1>
          <div className="flex items-center gap-4">
            <button
              onClick={() => setShowCreateForm(!showCreateForm)}
              className="text-sm font-medium text-gray-900 underline"
            >
              {showCreateForm ? "Cancel" : "+ New account"}
            </button>
            <button
              onClick={handleLogout}
              className="text-sm font-medium text-gray-600 hover:text-gray-900"
            >
              Log out
            </button>
          </div>
        </div>

        {showCreateForm && (
          <form
            onSubmit={handleCreateAccount}
            className="mb-6 flex items-center gap-3 rounded-lg border border-gray-200 bg-white p-4 shadow-sm"
          >
            <select
              value={newAccountType}
              onChange={(e) => setNewAccountType(e.target.value)}
              className="rounded-md border border-gray-300 px-3 py-2 text-sm"
            >
              <option value="CHECKING">Checking</option>
              <option value="SAVINGS">Savings</option>
            </select>
            <button
              type="submit"
              disabled={creating}
              className="rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
            >
              {creating ? "Creating..." : "Create"}
            </button>
          </form>
        )}

        {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

        {accounts.length === 0 ? (
          <p className="text-gray-500">You don&apos;t have any accounts yet.</p>
        ) : (
          <div className="space-y-3">
            {accounts.map((account) => (
              <a
                key={account.id}
                href={`/accounts/${account.id}`}
                className="block rounded-lg border border-gray-200 bg-white p-5 shadow-sm transition hover:border-gray-300"
              >
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-gray-500">{account.accountType}</p>
                    <p className="text-xs text-gray-400">····{account.accountNumber.slice(-4)}</p>
                  </div>
                  <p className="text-xl font-semibold text-gray-900">
                    {formatCents(account.balanceCents)}
                  </p>
                </div>
              </a>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
