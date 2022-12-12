(ns ions.utils
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [datomic.client.api :as d]
            [cognitect.anomalies :as anomalies]
            [io.pedestal.log :as log]))

(def client-config-path "ions/client-config.edn")

(def get-client
  (memoize
    #(if-let [r (io/resource client-config-path)]
       (d/client (edn/read-string (slurp r)))
       (throw (RuntimeException. (str "Missing " client-config-path))))))

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

(defn with-retry [op]
  (loop [attempt 1]
    (let [[success val] (try
                          [true (op)]
                          (catch Exception e
                            [false e]))]
      (if success
        val
        (if-let [ms (and (-> val ex-data ::anomalies/category retryable-anomaly?)
                         (get retry-wait-ms attempt))]
          (do
            (log/info :message (str "exception in attempt #" attempt ", retrying in " ms "ms")
                      :exception val)
            (Thread/sleep ms)
            (recur (inc attempt)))
          (throw val))))))

(comment
  (with-retry #(throw (ex-info "demo" {::anomalies/category ::anomalies/busy}))))

(defn list-databases []
  (with-retry #(d/list-databases (get-client) {:timeout 1000 :limit -1})))

(comment
  (list-databases))

(defn get-connection [db-name]
  (with-retry #(d/connect (get-client) {:db-name db-name})))

(defn get-schema [db]
  (->> (d/pull db '{:eid 0 :selector [{:db.install/attribute [*]}]})
       :db.install/attribute
       (map #(update % :db/valueType :db/ident))
       (map #(update % :db/cardinality :db/ident))))

