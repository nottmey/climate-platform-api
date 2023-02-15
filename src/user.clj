(ns user
  (:require
   [clojure.pprint :as pp]
   [clojure.set :as set]
   [clojure.test :refer [*testing-vars*]]
   [datomic.access :as access]
   [datomic.attributes :as da]
   [datomic.client.api :as d])
  (:import
   (clojure.lang ExceptionInfo)))

(defn pprint [o]
  (pp/pprint o))

(defn test-mode? []
  (boolean (seq *testing-vars*)))

(def rel-type "PlanetaryBoundary")
(def rel-field "name")
(def rel-attribute :platform/name)
(def rel-sample-value (str rel-attribute " sample value"))

(defn temp-conn
  ([] (temp-conn "testing"))
  ([db-name]
   (let [client  (d/client {:server-type :dev-local
                            :storage-dir :mem
                            :system      db-name})
         arg-map {:db-name db-name}]
     (d/delete-database client arg-map)
     (d/create-database client arg-map)
     (let [conn (d/connect client arg-map)]
       (d/transact conn {:tx-data da/graphql-attributes})
       (d/transact conn {:tx-data da/platform-attributes})
       (d/transact conn {:tx-data (da/add-value-field-tx-data rel-type rel-field rel-attribute)})
       conn))))

(comment
  (temp-conn))

(defn temp-db
  ([] (d/db (temp-conn)))
  ([db-name] (d/db (temp-conn db-name))))

(defn empty-tx-result [conn reason]
  (let [db (d/db conn)]
    {:db-before    db
     :db-after     db
     :tx-data      []
     :tempids      {}
     :empty-reason reason}))

(defn ensure-schema
  ([tx-data db-name]
   {:pre [(every? map? tx-data)
          (every? :db/ident tx-data)]}
   (let [conn           (access/get-connection db-name)
         test-tx-result (d/with (d/with-db conn) {:tx-data tx-data})]
     (if (<= (count (:tx-data test-tx-result)) 1)
       (empty-tx-result conn "empty transaction")
       (d/transact conn {:tx-data tx-data})))))

(defn ensure-data
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
  (get-tx-log 1 access/dev-env-db-name))

(defn get-db-stats
  ([db-name]
   (let [db (d/db (access/get-connection db-name))]
     (d/db-stats db))))

(comment
  (get-db-stats access/dev-env-db-name))

(defn get-schema
  ([db-name]
   (access/get-schema (d/db (access/get-connection db-name)))))

(comment
  (get-schema access/dev-env-db-name))
