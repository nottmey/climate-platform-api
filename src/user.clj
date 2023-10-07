(ns user
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.test :refer [*testing-vars* deftest is]]
   [datomic.access :as access]
   [datomic.attributes :as attributes]
   [datomic.client.api :as d]
   [datomic.temp :as temp]
   [datomic.tx-fns :as tx-fns]))

(comment
  ; example of historic values
  (d/q '[:find ?e ?v ?t ?add
         :where
         [?e :platform/name "Climate Change"]
         [?e :platform/description ?v ?t ?add]]
       (d/history (d/db (access/get-connection access/dev-env-db-name)))))

(defn test-mode? []
  (boolean (seq *testing-vars*)))

(deftest is-test-mode-on-test
  (is (test-mode?)))

(defn local-mode? "returns true, if we are in repl or testing" []
  (= (System/getProperty "local.mode") "true"))

(deftest is-local-mode-on-test
  (is (local-mode?)))

(def test-type-planetary-boundary "PlanetaryBoundary")
(def test-type-quantification "Quantification")
(def test-field-name "name")
(def test-field-name-value-1 " :platform/name sample value\n")
(def test-field-name-value-2 " some other \n value")
(def test-field-quantifications "quantifications")
(def test-field-planetary-boundaries "planetaryBoundaries")
(def test-attribute-name :platform/name)
(def test-attribute-quantifications :platform/quantifications)

(defn temp-conn []
  (let [conn (temp/conn)]
    ; TODO use golden snapshot of attributes file
    (d/transact conn {:tx-data attributes/graphql-attributes})
    (d/transact conn {:tx-data attributes/platform-attributes})
    ; TODO use golden snapshot of framework file
    (d/transact conn {:tx-data (tx-fns/create-type (d/db conn) test-type-planetary-boundary)})
    (d/transact conn {:tx-data (tx-fns/create-type (d/db conn) test-type-quantification)})
    (d/transact conn {:tx-data (tx-fns/add-field
                                (d/db conn)
                                [:graphql.type/name test-type-planetary-boundary]
                                test-field-name
                                test-attribute-name)})
    (d/transact conn {:tx-data (tx-fns/add-field
                                (d/db conn)
                                [:graphql.type/name test-type-quantification]
                                test-field-name
                                test-attribute-name)})
    (d/transact conn {:tx-data (tx-fns/add-field
                                (d/db conn)
                                [:graphql.type/name test-type-planetary-boundary]
                                test-field-quantifications
                                test-attribute-quantifications
                                [:graphql.type/name test-type-quantification])})
    (d/transact conn {:tx-data (tx-fns/add-field
                                (d/db conn)
                                [:graphql.type/name test-type-quantification]
                                test-field-planetary-boundaries
                                test-attribute-quantifications
                                [:graphql.type/name test-type-planetary-boundary]
                                true)})
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
