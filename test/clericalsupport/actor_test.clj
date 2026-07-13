(ns clericalsupport.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [clericalsupport.actor :as actor]
            [clericalsupport.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-record! st {:record-id "R-1" :client-id "client-1"
                                :name "2024-payroll-archive"
                                :retention-expiry-day 400
                                :required-clearance-level :internal})
    st))

(deftest commits-a-post-expiry-destruction
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-destruction :stake :low
                 :record-id "R-1" :as-of-day 500}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-a-pre-expiry-destruction
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-destruction :stake :low
                 :record-id "R-1" :as-of-day 100}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-overrides-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-early-destruction :stake :high
                 :record-id "R-1"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
