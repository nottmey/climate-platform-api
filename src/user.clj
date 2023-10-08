(ns user
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [datomic.access :as access]
   [datomic.client.api :as d]))

(defn get-replayable-tx-log [conn]
  (let [idents   (->> (d/q '[:find ?e ?ident
                             :where [?e :db/ident ?ident]] (d/history (d/db conn)))
                      (into {}))
        identify #(get idents % %)]
    (->> (d/tx-range conn {})
         (map :data)
         (map #(->> %
                    (map
                     (fn [[e a v _ add?]]
                       [(if add? :db/add :db/retract) (identify e) (identify a) (identify v)]))
                    (filter
                     (fn [[_ _ a]]
                       (not= a :db/txInstant)))))
         (map vec))))

(comment
  (with-bindings {#'pprint/*print-right-margin* 120}
    (pprint/pprint
     (reverse
      (get-replayable-tx-log
       (access/get-connection access/dev-env-db-name)))
     (io/writer "tx-log.edn"))))

; TODO add to db browser
(defn get-human-readable-tx-log [n db-name]
  (let [conn       (access/get-connection db-name)
        db         (d/db conn)
        history    (d/history db)
        start      (->> (d/qseq '[:find ?tx :where [?tx :db/txInstant]] db)
                        (map first)
                        sort
                        reverse
                        (drop (dec n))
                        (first))
        idents     (->> (d/q '[:find ?e ?ident
                               :where [?e :db/ident ?ident]] db)
                        (into {}))
        names      (->> (d/q '[:find ?e ?name
                               :where [?e :platform/name ?name]] db)
                        (into {}))
        hist-names (->> (d/q '[:find ?e ?name
                               :where [?e :platform/name ?name]] history)
                        (into {}))
        identify   #(get idents % (get names % (get hist-names % %)))]
    (->> (d/tx-range conn {:start start})
         (map :data)
         (map #(map
                (fn [[e a v _ add?]]
                  [(if add? :db/add :db/retract) (identify e) (identify a) (identify v)])
                %)))))

(comment
  (get-human-readable-tx-log 3 access/dev-env-db-name))

; TODO add to db browser
(defn revert-txs [tx-t db-name]
  (let [conn     (access/get-connection db-name)
        tx-data  (-> conn (d/tx-range {:start tx-t
                                       :end   (inc tx-t)}) vec first :data)
        new-data (->> tx-data
                      (remove #(= (:e %) tx-t))
                      (map #(vector (if (:added %) :db/retract :db/add) (:e %) (:a %) (:v %))))]
    (when (not-empty new-data)
      (d/transact conn {:tx-data new-data}))))

(comment
  (revert-txs 13194139533424 access/dev-env-db-name)

  (revert-txs 13194139533425 access/dev-env-db-name))
