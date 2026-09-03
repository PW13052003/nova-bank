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
  return new Date(dateStr).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
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
      <div className="flex min-h-screen items-center justify-center">
        <p className="text-muted">Loading...</p>
      </div>
    );
  }

  if (error || !account) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <p className="text-debit">{error || "Account not found"}</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen px-6 py-10">
      <div className="mx-auto max-w-2xl">
        <button
          onClick={() => router.push("/dashboard")}
          className="text-sm text-muted hover:text-ink"
        >
          ← Back to accounts
        </button>

        <div className="mt-6 mb-10">
          <p className="text-sm text-muted">
            {account.accountType} ····{account.accountNumber.slice(-4)}
          </p>
          <p className="mt-1 font-serif text-4xl text-ink">
            {formatCents(account.balanceCents)}
          </p>
        </div>

        <div className="mb-10 flex gap-6 border-y border-line py-4 text-sm">
          <button
            onClick={() => setActiveForm(activeForm === "deposit" ? null : "deposit")}
            className={activeForm === "deposit" ? "text-ink underline underline-offset-2" : "text-muted hover:text-ink"}
          >
            Deposit
          </button>
          <button
            onClick={() => setActiveForm(activeForm === "withdraw" ? null : "withdraw")}
            className={activeForm === "withdraw" ? "text-ink underline underline-offset-2" : "text-muted hover:text-ink"}
          >
            Withdraw
          </button>
          <button
            onClick={() => setActiveForm(activeForm === "transfer" ? null : "transfer")}
            className={activeForm === "transfer" ? "text-ink underline underline-offset-2" : "text-muted hover:text-ink"}
          >
            Transfer
          </button>
        </div>

        {activeForm && (
          <form onSubmit={handleAction} className="mb-10 space-y-6 border-b border-line pb-10">
            {activeForm === "transfer" && (
              <div>
                <label className="block text-sm text-muted">To account ID</label>
                <input
                  type="text"
                  value={toAccountId}
                  onChange={(e) => setToAccountId(e.target.value)}
                  required
                  className="mt-2 w-full border-0 border-b border-line bg-transparent py-1.5 text-ink focus:border-ink focus:outline-none"
                />
              </div>
            )}

            <div>
              <label className="block text-sm text-muted">Amount (USD)</label>
              <input
                type="number"
                step="0.01"
                min="0.01"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                required
                className="mt-2 w-full border-0 border-b border-line bg-transparent py-1.5 text-ink focus:border-ink focus:outline-none"
              />
            </div>

            {actionError && <p className="text-sm text-debit">{actionError}</p>}

            <button
              type="submit"
              disabled={submitting}
              className="bg-ink px-6 py-2.5 text-sm font-medium text-paper hover:bg-ink/90 disabled:opacity-50"
            >
              {submitting ? "Processing..." : `Confirm ${activeForm}`}
            </button>
          </form>
        )}

        <h2 className="mb-4 text-sm text-muted">Transaction history</h2>

        {transactions.length === 0 ? (
          <p className="text-sm text-muted">No transactions yet.</p>
        ) : (
          <div className="divide-y divide-line border-t border-line">
            {transactions.map((txn) => (
              <div key={txn.transactionId} className="flex items-center justify-between py-4">
                <div>
                  <p className="text-sm text-ink">{txn.type}</p>
                  <p className="text-xs text-muted">{formatDate(txn.createdAt)}</p>
                </div>
                <p className={txn.amountCents >= 0 ? "text-sm text-credit" : "text-sm text-debit"}>
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