(ns datomic.utils
  (:require
   [clojure.test :refer [deftest is]]
   [cognitect.anomalies :as anomalies]
   [io.pedestal.log :as log])
  (:import (clojure.lang ExceptionInfo)))

(def retryable-anomaly?
  #{::anomalies/busy
    ::anomalies/unavailable
    ::anomalies/interrupted})

(def retry-wait-ms
  {1 100
   2 200
   3 400
   4 800
   5 1600
   6 3200
   7 6200
   8 12400})

(defn sleep [ms _attempt]
  (Thread/sleep ms))

(defn with-retry [operation]
  (loop [attempt 1]
    (let [[success val] (try
                          [true (operation)]
                          (catch Exception e
                            [false e]))]
      (if success
        val
        (if-let [ms (and (-> val ex-data ::anomalies/category retryable-anomaly?)
                         (get retry-wait-ms attempt))]
          (do
            (if (-> val ex-message (= "Datomic Client Timeout"))
              (log/info :message (str "timeout in attempt #" attempt ", retrying in " ms "ms"))
              (log/info :message (str "exception in attempt #" attempt ", retrying in " ms "ms") :exception val))
            (sleep ms attempt)
            (recur (inc attempt)))
          (throw val))))))

(declare thrown?)
(deftest with-retry-test
  (let [retries (atom {})]
    (with-redefs [sleep (fn [ms attempt] (swap! retries assoc attempt ms))]
      (is (= "something" (with-retry #(str "something"))))
      (is (= {} @retries))

      (is (thrown? Exception (with-retry #(throw (Exception.)))))
      (is (= {} @retries))

      (is (thrown?
           ExceptionInfo
           (with-retry #(throw (ex-info
                                "Datomic Client Timeout"
                                {::anomalies/category ::anomalies/busy})))))
      (is (= retry-wait-ms @retries))

      (reset! retries {})

      (is (thrown?
           ExceptionInfo
           (with-retry #(throw (ex-info
                                "another exception"
                                {::anomalies/category ::anomalies/interrupted})))))
      (is (= retry-wait-ms @retries))

      (reset! retries {})

      (is (thrown?
           ExceptionInfo
           (with-retry #(throw (ex-info
                                "something unavailable"
                                {::anomalies/category ::anomalies/unavailable})))))
      (is (= retry-wait-ms @retries)))))
