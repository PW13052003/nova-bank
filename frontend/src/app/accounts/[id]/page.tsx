"use client";

import { useEffect, useState, use } from "react";
import { useRouter } from "next/navigation";
import { apiFetch } from "@/lib/api";

interface Account {
  id: string;
  accountNumber: string;
  accountType: string;
  status: string;
  balanceCents: number;
}

interface Transaction {
  transactionId: string;
  type: string;
  status: string;
  description: string;
  amountCents: number;
  balanceAfter: number;
  createdAt: string;
}

function formatCents(cents: number) {
  return (cents / 100).toLocaleString("en-US", { style: "currency", currency: "USD" });
}

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleString();
}

export default function AccountDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const router = useRouter();

  const [account, setAccount] = useState<Account | null>(null);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");

  const [activeForm, setActiveForm] = useState<"deposit" | "withdraw" | "transfer" | null>(null);
  const [amount, setAmount] = useState("");
  const [toAccountId, setToAccountId] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    loadData();
  }, [id]);

  async function loadData() {
    try {
      const accounts: Account[] = await apiFetch("/accounts");
      const found = accounts.find((a) => a.id === id);
      if (!found) {
        setError("Account not found");
        return;
      }
      setAccount(found);

      const history = await apiFetch(`/accounts/${id}/transactions`);
      setTransactions(history);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load account");
    } finally {
      setLoading(false);
    }
  }

  function resetForm() {
    setActiveForm(null);
    setAmount("");
    setToAccountId("");
    setActionError("");
  }

  async function handleAction(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setActionError("");

    const amountCents = Math.round(parseFloat(amount) * 100);
    const idempotencyKey = crypto.randomUUID();

    try {
      if (activeForm === "deposit") {
        await apiFetch("/deposit", {
          method: "POST",
          body: JSON.stringify({ accountId: id, amountCents, idempotencyKey }),
        });
      } else if (activeForm === "withdraw") {
        await apiFetch("/withdraw", {
          method: "POST",
          body: JSON.stringify({ accountId: id, amountCents, idempotencyKey }),
        });
      } else if (activeForm === "transfer") {
        await apiFetch("/transfer", {
          method: "POST",
          body: JSON.stringify({ fromAccountId: id, toAccountId, amountCents, idempotencyKey }),
        });
      }
      resetForm();
      await loadData();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "Action failed");
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50">
        <p className="text-gray-500">Loading...</p>
      </div>
    );
  }

  if (error || !account) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50">
        <p className="text-red-600">{error || "Account not found"}</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 px-6 py-10">
      <div className="mx-auto max-w-2xl">
        <button
          onClick={() => router.push("/dashboard")}
          className="mb-6 text-sm font-medium text-gray-600 hover:text-gray-900"
        >
          ← Back to accounts
        </button>

        <div className="mb-6 rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
          <p className="text-sm font-medium text-gray-500">{account.accountType}</p>
          <p className="text-xs text-gray-400">····{account.accountNumber.slice(-4)}</p>
          <p className="mt-2 text-3xl font-semibold text-gray-900">
            {formatCents(account.balanceCents)}
          </p>
        </div>

        <div className="mb-6 flex gap-3">
          <button
            onClick={() => setActiveForm(activeForm === "deposit" ? null : "deposit")}
            className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
          >
            Deposit
          </button>
          <button
            onClick={() => setActiveForm(activeForm === "withdraw" ? null : "withdraw")}
            className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
          >
            Withdraw
          </button>
          <button
            onClick={() => setActiveForm(activeForm === "transfer" ? null : "transfer")}
            className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
          >
            Transfer
          </button>
        </div>

        {activeForm && (
          <form
            onSubmit={handleAction}
            className="mb-6 space-y-3 rounded-lg border border-gray-200 bg-white p-5 shadow-sm"
          >
            <h2 className="text-sm font-semibold capitalize text-gray-900">{activeForm}</h2>

            {activeForm === "transfer" && (
              <div>
                <label className="block text-sm font-medium text-gray-700">To account ID</label>
                <input
                  type="text"
                  value={toAccountId}
                  onChange={(e) => setToAccountId(e.target.value)}
                  required
                  className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-gray-500 focus:outline-none"
                />
              </div>
            )}

            <div>
              <label className="block text-sm font-medium text-gray-700">Amount (USD)</label>
              <input
                type="number"
                step="0.01"
                min="0.01"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                required
                className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-gray-500 focus:outline-none"
              />
            </div>

            {actionError && <p className="text-sm text-red-600">{actionError}</p>}

            <button
              type="submit"
              disabled={submitting}
              className="w-full rounded-md bg-gray-900 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
            >
              {submitting ? "Processing..." : `Confirm ${activeForm}`}
            </button>
          </form>
        )}

        <h2 className="mb-3 text-lg font-semibold text-gray-900">Transaction history</h2>

        {transactions.length === 0 ? (
          <p className="text-sm text-gray-500">No transactions yet.</p>
        ) : (
          <div className="space-y-2">
            {transactions.map((txn) => (
              <div
                key={txn.transactionId}
                className="flex items-center justify-between rounded-lg border border-gray-200 bg-white p-4 shadow-sm"
              >
                <div>
                  <p className="text-sm font-medium text-gray-900">{txn.type}</p>
                  <p className="text-xs text-gray-400">{formatDate(txn.createdAt)}</p>
                </div>
                <p
                  className={`text-sm font-semibold ${
                    txn.amountCents >= 0 ? "text-green-600" : "text-red-600"
                  }`}
                >
                  {txn.amountCents >= 0 ? "+" : ""}
                  {formatCents(txn.amountCents)}
                </p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}