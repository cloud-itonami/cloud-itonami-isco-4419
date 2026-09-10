(ns clericalsupport.governor
  "ClericalSupportWorkersGovernor — the independent safety/
  traceability layer for the ISCO-08 4419 community clerical support
  workers (NEC) actor (itonami actor pattern, ADR-2607011000 /
  CLAUDE.md Actors section). Modeled on cloud-itonami-isco-4311's
  bookkeeping.governor. Records-management twist: destruction before
  the registered retention-expiry day is prohibited (records exist
  until the clock says otherwise, not until convenience says
  otherwise), and access requires an ordinally sufficient clearance
  level (:public < :internal < :confidential) — access control is
  ordinal, not discretion.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance — the organization must be registered.
    2. no-actuation      — proposal :effect must be :propose.
    3. record basis         — an approval must cite a REGISTERED
                           record belonging to this client.
    4. retention floor      — a proposed destruction's as-of day must
                           be >= the record's registered
                           :retention-expiry-day (records exist until
                           the clock says otherwise).
    5. clearance ordinal    — a proposed access's requester-clearance-level
                           must be ordinally >= the record's
                           registered :required-clearance-level
                           (:public < :internal < :confidential).
    6. comparability        — a floor that cannot be compared is not a
                           floor that has been met. See below.
  ESCALATION invariants (:escalate? true, human sign-off):
    7. :op :approve-early-destruction (legal-hold override request).
    8. low confidence (< `confidence-floor`).

  ## Why comparability is its own HARD invariant

  Invariants 4 and 5 are comparisons, and a comparison needs two
  comparable operands. Until 2026-09-10 this governor guarded each
  comparison with the operand's own presence test — `(integer?
  as-of-day)` for the floor, a truthy `requester-clearance-level` for
  the ordinal — so a proposal that simply **omitted** the field skipped
  the comparison and arrived at the human with `:violations []`.
  Measured on the version before that date, against a record with
  `:retention-expiry-day 400` and `:required-clearance-level
  :internal`:

    {:op :approve-destruction :effect :propose :record-id \"R-1\"}
      => {:ok? true}   ; destruction approved with no day at all
    {... :as-of-day \"400\"}
      => {:ok? true}   ; a string is not `integer?`, so no floor
    {:op :approve-access :effect :propose :record-id \"R-1\"}
      => {:ok? true}   ; access granted with no clearance at all
    {... :requester-clearance-level :secret}
      => NullPointerException   ; off-scale level, `(< nil 2)`

  Both HARD invariants were therefore bypassable by leaving the field
  out — the cheapest thing an advisor can do wrong — and an
  unregistered level crashed the `:govern` node rather than holding,
  so nothing reached the ledger at all. This is CLAUDE.md's first of
  the eight questions: what does the check return when the input is
  absent? Returning the same value as a check that ran and found
  nothing wrong is the defect.

  So the operands are now normalized through `day` and `rank`, both of
  which answer nil for anything they cannot place on the scale, and a
  nil operand raises `:retention-unverifiable` /
  `:clearance-unverifiable` rather than skipping the comparison. The
  refusals stay distinct from `:retention-not-expired` /
  `:clearance-insufficient` on purpose: `I cannot tell when you are
  destroying this` and `you are destroying this too early` are
  different facts, and a human reading the ledger has to be able to
  tell them apart."
  (:require [clericalsupport.store :as store]))

(def confidence-floor 0.6)

(def ^:private clearance-rank {:public 1 :internal 2 :confidential 3})

(defn- day
  "The comparable form of a day, or nil when there is none. Days are a
  simple monotonic integer clock; a string, a nil or a float is not a
  lenient day, it is a day this governor cannot compare."
  [x]
  (when (integer? x) x))

(defn- rank
  "The ordinal of a registered clearance level, or nil for a level this
  governor does not know. An unregistered level is not the bottom of
  the scale — it is off the scale, and off the scale is not below the
  floor, it is uncomparable to it."
  [x]
  (get clearance-rank x))

(defn- hard-violations [{:keys [request proposal]} client-record r]
  (let [{:keys [op as-of-day requester-clearance-level]} proposal
        destroy? (= :approve-destruction op)
        access? (= :approve-access op)
        rec-op? (or destroy? access?)
        ;; Both operands of each comparison, normalized. nil means "not
        ;; on the scale", which is refused below rather than skipped.
        proposed-day (day as-of-day)
        floor-day (day (:retention-expiry-day r))
        held-rank (rank requester-clearance-level)
        required-rank (rank (:required-clearance-level r))]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (and rec-op? (nil? r))
      (conj {:rule :unknown-record :detail "未登録 record への承認は不可"})

      (and rec-op? r (not= (:client-id r) (:client-id request)))
      (conj {:rule :record-wrong-client :detail "record が別 client のもの"})

      ;; Comparability before comparison. An absent or non-integer day
      ;; on either side means the floor cannot be evaluated at all —
      ;; and a floor that cannot be evaluated has not been met.
      (and destroy? r (or (nil? proposed-day) (nil? floor-day)))
      (conj {:rule :retention-unverifiable
             :detail (str "保存期限を比較できない（提案日 " (pr-str as-of-day)
                          " / 登録済み保存期限 " (pr-str (:retention-expiry-day r))
                          "）。比較できない床は、満たされた床ではない")})

      (and destroy? proposed-day floor-day (< proposed-day floor-day))
      (conj {:rule :retention-not-expired
             :detail (str "day " proposed-day " < 保存期限 " floor-day
                          "（記録は時計が許すまで存続する。都合ではない）")})

      ;; Same shape for the ordinal: an unregistered level is off the
      ;; scale, not at the bottom of it, and off the scale cannot be
      ;; compared to the requirement.
      (and access? r (or (nil? held-rank) (nil? required-rank)))
      (conj {:rule :clearance-unverifiable
             :detail (str "クリアランスを比較できない（要求者 "
                          (pr-str requester-clearance-level) " / 登録済み要求水準 "
                          (pr-str (:required-clearance-level r))
                          "）。尺度に載らない値は、尺度の下端ではない")})

      (and access? held-rank required-rank (< held-rank required-rank))
      (conj {:rule :clearance-insufficient
             :detail (str "要求者クリアランス " requester-clearance-level " < 登録済み要求水準 "
                          (:required-clearance-level r)
                          "（アクセス制御は順序尺度であって裁量ではない）")}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `clericalsupport.store/Store`. Pure — never
  mutates the store."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        r (some->> (:record-id proposal) (store/record store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record r)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        risky-op? (= :approve-early-destruction (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))
