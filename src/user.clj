(ns user
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [datomic.access :as access]
   [datomic.client.api :as d]
   [datomic.temp :as temp]))

(comment
  ; example of historic values
  (d/q '[:find ?e ?v ?t ?add
         :where
         [?e :platform/name "Climate Change"]
         [?e :platform/description ?v ?t ?add]]
       (d/history (d/db (access/get-connection access/dev-env-db-name)))))

(def golden-attributes-file (io/resource "goldens/attributes.edn"))
(def golden-framework-file (io/resource "goldens/framework.edn"))

(defn apply-golden-attributes-file [conn]
  (d/transact conn {:tx-data (edn/read-string (slurp golden-attributes-file))}))

(defn apply-golden-framework-file [conn]
  (d/transact conn {:tx-data (edn/read-string (slurp golden-framework-file))}))

(defn temp-conn []
  (let [conn (temp/conn)]
    (apply-golden-attributes-file conn)
    (apply-golden-framework-file conn)
    conn))

(comment
  (temp-conn))

(defn temp-db []
  (d/db (temp-conn)))

(comment
  (temp-db))

(def testing-conn
  "default conn to be used in testing,
   or to be redefined when a different scenario is needed"
  temp-conn)

(defn testing-publish
  "default publish callback used in testing,
   or to be redefined when a different scenario is needed"
  [& _]
  (throw (AssertionError. "no call to publish expected")))

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
