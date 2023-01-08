(ns datomic.utils
  (:require [cognitect.anomalies :as anomalies]
            [io.pedestal.log :as log]))

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
          (let [regular? (-> val .getMessage (= "Datomic Client Timeout"))]
            (log/info :message (str "exception in attempt #" attempt ", retrying in " ms "ms")
                      :exception (if regular? nil val))
            (Thread/sleep ms)
            (recur (inc attempt)))
          (throw val))))))

(comment
  (with-retry #(throw (ex-info "demo" {::anomalies/category ::anomalies/busy}))))