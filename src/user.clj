(ns user
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.set :as set]
   [clojure.test :refer [*testing-vars*]]
   [datomic.access :as access]
   [datomic.attributes :as attributes]
   [datomic.client.api :as d]
   [datomic.temp :as temp]
   [datomic.tx-fns :as tx-fns])
  (:import
   (clojure.lang ExceptionInfo)))

(comment
  ; example of historic values
  (d/q '[:find ?e ?v ?t ?add
         :where
         [?e :platform/name "Climate Change"]
         [?e :platform/description ?v ?t ?add]]
       (d/history (d/db (access/get-connection access/dev-env-db-name)))))

(defn test-mode? []
  (boolean (seq *testing-vars*)))

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
    (d/transact conn {:tx-data attributes/graphql-attributes})
    (d/transact conn {:tx-data attributes/platform-attributes})
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
  []
  (throw (AssertionError. "no call to publish expected")))

(defn test-collection [conn entity-type]
  (-> (d/pull (d/db conn) '[:graphql.type/collection] [:graphql.type/name entity-type])
      (get-in [:graphql.type/collection :db/id])))

(comment
  (test-collection (temp-conn) test-type-planetary-boundary))

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

(comment
  (ensure-schema attributes/platform-attributes access/dev-env-db-name))

(defn ensure-data
  ([tx-data db-name]
   {:pre [(every? map? tx-data)]}
   (let [conn               (access/get-connection db-name)
         attributes         (->> (d/pull (d/db conn) '{:eid      0
                                                       :selector [{:db.install/attribute [*]}]})
                                 :db.install/attribute
                                 (map #(update % :db/valueType :db/ident))
                                 (map #(update % :db/cardinality :db/ident)))
         unique-attrs       (->> attributes
                                 (filter :db/unique)
                                 (map :db/ident)
                                 (map hash-set))
         unique-tuple-attrs (->> attributes
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

; TODO add to db browser
(defn get-tx-log
  ([n db-name]
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
                   [(identify e) (identify a) (identify v) (if add? :added :removed)])
                 %))))))

(comment
  (get-tx-log 12 access/dev-env-db-name))

; TODO add to db browser
(defn revert-txs [tx-t db-name]
  (let [conn     (access/get-connection db-name)
        tx-data  (-> conn (d/tx-range {:start tx-t
                                       :end (inc tx-t)}) vec first :data)
        new-data (->> tx-data
                      (remove #(= (:e %) tx-t))
                      (map #(vector (if (:added %) :db/retract :db/add) (:e %) (:a %) (:v %))))]
    (when (not-empty new-data)
      (d/transact conn {:tx-data new-data}))))

(comment
  (revert-txs 13194139533395 access/dev-env-db-name))

(defn get-db-stats
  ([db-name]
   (let [db (d/db (access/get-connection db-name))]
     (d/db-stats db))))

(comment
  (get-db-stats access/dev-env-db-name))

(defn download-framework-data []
  (let [conn (access/get-connection access/dev-env-db-name)
        db   (d/db conn)
        data (vec
              (concat
               (->> (d/q '[:find (pull ?attr [*])
                           :where [_ :graphql.relation/attribute ?attr]]
                         db)
                    (map first)
                    (map #(dissoc % :db/id))
                    (map #(update % :db/cardinality get :db/ident))
                    (map #(update % :db/valueType get :db/ident)))
               (->> (d/q '[:find (pull ?type [*])
                           :where [?type :graphql.type/name]]
                         db)
                    (map first)
                    (map #(assoc % :db/id (:graphql.type/name %))))
               (->> (d/q '[:find (pull ?rel [* {:graphql.relation/type [*]}])
                           :where [?rel :graphql.relation/type]]
                         db)
                    (map first)
                    (map #(dissoc % :db/id :graphql.relation/type+field))
                    (map #(update % :graphql.relation/attribute get :db/ident))
                    (map #(update % :graphql.relation/type get :graphql.type/name)))))]
    (->> (with-out-str (pprint/pprint data))
         (spit (io/resource "datomic/framework-data.edn")))))

(comment
  (download-framework-data))

(defn download-example-data []
  (let [conn (access/get-connection access/dev-env-db-name)
        data (->> (d/q '[:find (pull ?e [*])
                         :where [?e :platform/id]]
                       (d/db conn))
                  (map first)
                  (map #(dissoc % :db/id))
                  (vec))]
    (->> (with-out-str (pprint/pprint data))
         (spit (io/resource "datomic/example-data.edn")))))

(comment
  (download-example-data))

