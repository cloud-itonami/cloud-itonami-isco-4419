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
  ESCALATION invariants (:escalate? true, human sign-off):
    6. :op :approve-early-destruction (legal-hold override request).
    7. low confidence (< `confidence-floor`)."
  (:require [clericalsupport.store :as store]))

(def confidence-floor 0.6)

(def ^:private clearance-rank {:public 1 :internal 2 :confidential 3})

(defn- hard-violations [{:keys [request proposal]} client-record r]
  (let [{:keys [op as-of-day requester-clearance-level]} proposal
        destroy? (= :approve-destruction op)
        access? (= :approve-access op)
        rec-op? (or destroy? access?)]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (and rec-op? (nil? r))
      (conj {:rule :unknown-record :detail "未登録 record への承認は不可"})

      (and rec-op? r (not= (:client-id r) (:client-id request)))
      (conj {:rule :record-wrong-client :detail "record が別 client のもの"})

      (and destroy? r (integer? as-of-day) (< as-of-day (:retention-expiry-day r)))
      (conj {:rule :retention-not-expired
             :detail (str "day " as-of-day " < 保存期限 " (:retention-expiry-day r)
                          "（記録は時計が許すまで存続する。都合ではない）")})

      (and access? r requester-clearance-level
           (< (clearance-rank requester-clearance-level)
              (clearance-rank (:required-clearance-level r))))
      (conj {:rule :clearance-insufficient
             :detail (str "要求者クリアランス " requester-clearance-level " < 登録済み要求水準 "
                          (:required-clearance-level r) "（アクセス制御は順序尺度であって裁量ではない）")}))))

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
