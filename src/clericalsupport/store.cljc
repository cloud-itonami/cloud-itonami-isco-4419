(ns clericalsupport.store
  "SSoT for the ISCO-08 4419 community clerical support workers (NEC)
  actor (itonami actor pattern, ADR-2607011000 / CLAUDE.md Actors
  section). Modeled on cloud-itonami-isco-4311's bookkeeping.store.

  Domain:

    client — a registered organization (:client-id, :name)
    record — a registered filed record {:record-id :client-id :name
             :retention-expiry-day int
             :required-clearance-level :public|:internal|:confidential}.
             `:retention-expiry-day` is the registered earliest day
             (simple monotonic day clock, day 0 = epoch for this
             record) destruction may occur — records exist until the
             clock says otherwise, not until convenience says
             otherwise; `:required-clearance-level` is the registered
             ordinal floor a proposed access's requester clearance
             must meet or exceed.
    op-record — a committed operating record (approved destruction or
             access) — written ONLY via commit-record!.
    ledger — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (record [s record-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-record! [s r])
  (commit-record! [s op-record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (record [_ record-id] (get-in @a [:records record-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:op-records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-record! [s r]
    (swap! a assoc-in [:records (:record-id r)] r) s)
  (commit-record! [s op-record]
    (swap! a update :op-records (fnil conj []) op-record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :records {} :op-records [] :ledger []}
                                   seed)))))
