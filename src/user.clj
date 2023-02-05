(ns user
  (:require
   [clojure.set :as set]
   [datomic.access :as access]
   [datomic.client.api :as d])
  (:import
   (clojure.lang ExceptionInfo)))

(def sandbox-env-db-name "sandbox")

(defn sandbox-conn []
  (access/get-connection sandbox-env-db-name))

(defn sandbox-db []
  (d/db (sandbox-conn)))

(defn create-sandbox []
  (d/create-database (access/get-client) {:db-name sandbox-env-db-name}))

(comment
  (create-sandbox))

; TODO backup data regularly and test recovery
(defn delete-sandbox []
  (d/delete-database (access/get-client) {:db-name sandbox-env-db-name}))

(comment
  (delete-sandbox))

(defn empty-tx-result [conn reason]
  (let [db (d/db conn)]
    {:db-before    db
     :db-after     db
     :tx-data      []
     :tempids      {}
     :empty-reason reason}))

(defn ensure-schema
  ([tx-data] (ensure-schema tx-data sandbox-env-db-name))
  ([tx-data db-name]
   {:pre [(every? map? tx-data)
          (every? :db/ident tx-data)]}
   (let [conn           (access/get-connection db-name)
         test-tx-result (d/with (d/with-db conn) {:tx-data tx-data})]
     (if (<= (count (:tx-data test-tx-result)) 1)
       (empty-tx-result conn "empty transaction")
       (d/transact conn {:tx-data tx-data})))))

(defn ensure-data
  ([tx-data] (ensure-data tx-data sandbox-env-db-name))
  ([tx-data db-name]
   {:pre [(every? map? tx-data)]}
   (let [conn               (access/get-connection db-name)
         schema             (access/get-schema (d/db conn))
         unique-attrs       (->> (vals schema)
                                 (filter :db/unique)
                                 (map :db/ident)
                                 (map hash-set))
         unique-tuple-attrs (->> (vals schema)
                                 (filter :db/unique)
                                 (filter :db/tupleAttrs)
                                 (map :db/tupleAttrs)
                                 (map set))
         uniques-sets       (concat unique-attrs unique-tuple-attrs)
         has-uniqueness?    (fn [tx-map]
                              (let [tx-keys (into #{} (keys tx-map))]
                                (some #(set/superset? tx-keys %) uniques-sets)))]
     (assert (every? has-uniqueness? tx-data))
     (try
       (let [test-tx-result (d/with (d/with-db conn) {:tx-data tx-data})]
         (if (<= (count (:tx-data test-tx-result)) 1)
           (empty-tx-result conn "empty transaction")
           (d/transact conn {:tx-data tx-data})))
       (catch ExceptionInfo e
         (if (= (:db/error (ex-data e))
                :db.error/unique-conflict)
           (empty-tx-result conn "conflict")
           (throw e)))))))

(defn get-tx-log
  ([] (get-tx-log 1))
  ([n] (get-tx-log n sandbox-env-db-name))
  ([n db-name]
   (let [conn     (access/get-connection db-name)
         db       (d/db conn)
         start    (->> (d/qseq '[:find ?tx :where [?tx :db/txInstant]] db)
                       (map first)
                       sort
                       reverse
                       (drop (dec n))
                       (first))
         idents   (->> (d/q '[:find ?e ?ident
                              :where [?e :db/ident ?ident]] db)
                       (into {}))
         identify #(get idents % %)]
     (->> (d/tx-range conn {:start start})
          (map :data)
          (map #(map
                 (fn [[e a v add? tx]]
                   [(identify e) (identify a) (identify v) add? tx])
                 %))))))

(comment
  (get-tx-log 1 #_access/dev-env-db-name))

(defn get-db-stats
  ([] (get-db-stats sandbox-env-db-name))
  ([db-name]
   (let [db (d/db (access/get-connection db-name))]
     (d/db-stats db))))

(comment
  (get-db-stats #_access/dev-env-db-name))

(defn get-schema
  ([] (get-schema sandbox-env-db-name))
  ([db-name]
   (access/get-schema (d/db (access/get-connection db-name)))))

(comment
  (get-schema #_access/dev-env-db-name))
