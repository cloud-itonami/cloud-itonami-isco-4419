(ns clericalsupport.advisor
  "ClericalSupportWorkersAdvisor — proposes a records-management
  operation (approve a destruction, approve an access, approve an
  early destruction) for a registered organization. Swappable
  mock/llm; the advisor ONLY proposes — `clericalsupport.governor`
  checks the retention floor and clearance ordinal independently.
  Modeled on cloud-itonami-isco-4311's advisor.

  A proposal: {:op :approve-destruction|:approve-access|:approve-early-destruction
               :effect :propose :record-id str :as-of-day int
               :requester-clearance-level :public|:internal|:confidential
               :stake kw :confidence n :rationale str}")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake record-id as-of-day requester-clearance-level] :as request}]
  {:op op
   :effect :propose
   :record-id record-id
   :as-of-day as-of-day
   :requester-clearance-level requester-clearance-level
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a clerical support/records-management advisor. Given a
   request, propose an :op, the :record-id, :as-of-day and
   :requester-clearance-level, an honest :confidence and a :stake.
   Never call early destruction or insufficient-clearance access
   conforming — the governor checks both against the registered
   record.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
