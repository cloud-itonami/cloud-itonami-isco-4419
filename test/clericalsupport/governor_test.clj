(ns clericalsupport.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [clericalsupport.store :as store]
            [clericalsupport.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-record! st {:record-id "R-1" :client-id "client-1"
                                :name "2024-payroll-archive"
                                :retention-expiry-day 400
                                :required-clearance-level :internal})
    st))

(defn- destroy [day]
  {:op :approve-destruction :effect :propose :record-id "R-1"
   :as-of-day day :confidence 0.9 :stake :low})

(defn- access [level]
  {:op :approve-access :effect :propose :record-id "R-1"
   :requester-clearance-level level :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-destruction-at-exact-expiry
  (testing "destruction at the retention expiry boundary is permitted"
    (let [st (fresh-store)
          v (governor/check req {} (destroy 400) st)]
      (is (:ok? v)))))

(deftest ok-destruction-after-expiry
  (let [st (fresh-store)
        v (governor/check req {} (destroy 500) st)]
    (is (:ok? v))))

(deftest hard-on-early-destruction
  (testing "records exist until the clock says otherwise, not until convenience says otherwise"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (destroy 100) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :retention-not-expired (:rule %)) (:violations v))))))

(deftest ok-access-at-exact-required-level
  (let [st (fresh-store)
        v (governor/check req {} (access :internal) st)]
    (is (:ok? v))))

(deftest ok-access-above-required-level
  (testing "confidential clearance exceeds an internal requirement and is granted"
    (let [st (fresh-store)
          v (governor/check req {} (access :confidential) st)]
      (is (:ok? v)))))

(deftest hard-on-clearance-insufficient
  (testing "access control is ordinal, not discretion"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (access :public) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :clearance-insufficient (:rule %)) (:violations v))))))

(deftest hard-on-unknown-record
  (let [st (fresh-store)
        v (governor/check req {} (assoc (destroy 500) :record-id "R-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-record (:rule %)) (:violations v)))))

(deftest hard-on-foreign-record
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (destroy 500) st)]
      (is (:hard? v))
      (is (some #(= :record-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (destroy 500) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (destroy 500) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest escalates-early-destruction-override
  (let [st (fresh-store)
        v (governor/check req {} {:op :approve-early-destruction :effect :propose
                                  :record-id "R-1" :confidence 0.9 :stake :high} st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (destroy 500) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
