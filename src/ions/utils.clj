(ns ions.utils
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [datomic.client.api :as d]
            [cognitect.anomalies :as anomalies]
            [io.pedestal.log :as log]))

(def client-config-path "ions/client-config.edn")

(def database-name "datomic-docs-tutorial")

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

(defn get-connection []
  (with-retry #(d/connect (get-client) {:db-name database-name})))

(defn get-db []
  (d/db (get-connection)))

(defn get-schema [db]
  (->> (d/pull db '{:eid 0 :selector [{:db.install/attribute [*]}]})
       :db.install/attribute
       (remove (fn [m] (str/starts-with? (namespace (:db/ident m)) "db")))
       (map #(update % :db/valueType :db/ident))
       (map #(update % :db/cardinality :db/ident))))
