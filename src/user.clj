(ns user
  (:require [datomic.access :as access]
            [datomic.client.api :as d]))

(def sandbox "sandbox")

(defn sandbox-conn []
  (access/get-connection sandbox))

(defn sandbox-db []
  (d/db (sandbox-conn)))

(defn create-sandbox []
  (d/create-database (access/get-client) {:db-name sandbox}))

(comment
  (create-sandbox))

; TODO backup data regularly and test recovery
(defn delete-sandbox []
  (d/delete-database (access/get-client) {:db-name sandbox}))

(comment
  (delete-sandbox))

(defn get-sandbox-tx-log [n]
  (let [conn     (access/get-connection sandbox)
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
                 %)))))

(comment
  (get-sandbox-tx-log 1))

(defn get-sandbox-stats []
  (let [db (d/db (access/get-connection sandbox))]
    (d/db-stats db)))

(comment
  (get-sandbox-stats))

(defn get-sandbox-schema []
  (access/get-schema (d/db (access/get-connection sandbox))))

(comment
  (get-sandbox-schema))
