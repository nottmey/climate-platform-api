(ns ions.resolvers
  (:require
    [datomic.client.api :as d]
    [ions.mappings :as mappings]
    [ions.utils :as utils]))

(defn- extract-type-field-tuple [{:keys [parent-type-name field-name]}]
  [parent-type-name field-name])

(def resolvable-paths (atom #{}))

(comment
  (seq @resolvable-paths))

; TODO these are only static resolvers, maybe we want dynamic ones (based on data) too
(defmulti datomic-resolve extract-type-field-tuple)

(defmacro defresolver [multifn dispatch-val & fn-tail]
  (swap! resolvable-paths conj dispatch-val)
  `(defmethod ~multifn ~dispatch-val ~@fn-tail))

(defresolver datomic-resolve [:Query :databases] [_]
  (utils/list-databases))

(comment
  (datomic-resolve {:parent-type-name :Query :field-name :databases}))

(defresolver datomic-resolve [:Query :get] [{:keys [arguments]}]
  (let [{:keys [database id]} arguments
        db     (d/db (utils/get-connection database))
        schema (utils/get-schema db)
        result (d/pull db '[*] (parse-long (str id)))]
    (mappings/map-entity result schema)))

(comment
  (datomic-resolve {:parent-type-name :Query
                    :field-name       :get
                    :arguments        {:database (first (utils/list-databases))
                                       :id       "0"}}))

(defn recently-updated-entities [db schema]
  (->> (d/q '[:find ?e (max ?t)
              :in $ [?as ...]
              :where [?e ?as _ ?t]]
            db
            (keys schema))
       (sort-by second)
       (reverse)
       (map first)))

(defn pull-entities [db es]
  (->> es
       (map-indexed vector)
       (d/q '[:find ?i (pull ?e [*])
              :in $ [[?i ?e]]] db)
       (sort-by first)
       (map second)))

(comment
  (let [db     (d/db (utils/get-connection (first (utils/list-databases))))
        schema (utils/get-schema db)
        es     (time (recently-updated-entities db schema))]
    (count (time (doall (pull-entities db es))))))

(defresolver datomic-resolve [:Query :list] [{:keys [arguments]}]
  (let [{:keys [database]} arguments
        db     (d/db (utils/get-connection database))
        schema (utils/get-schema db)]
    {"total" (count (recently-updated-entities db schema))
     ; handing down context args to the field resolver (needed because parameterizable)
     "page"  {"database" database "t" (:t db)}}))

(comment
  (datomic-resolve {:parent-type-name :Query
                    :field-name       :list
                    :arguments        {:database (first (utils/list-databases))}}))

(defresolver datomic-resolve [:EntityList :page] [{:keys [arguments parent-value]}]
  (let [{:keys [page size]} arguments
        database (get-in parent-value [:page :database])
        t        (get-in parent-value [:page :t])
        db       (d/as-of (d/db (utils/get-connection database)) t)
        schema   (utils/get-schema db)
        entities (recently-updated-entities db schema)
        total    (count entities)
        size     (min 100 (max 1 (or size 20)))
        last     (if (pos? total)
                   (dec (int (Math/ceil (/ total size))))
                   0)
        page     (min last (max 0 (or page 0)))
        offset   (* page size)
        entities (->> entities
                      (drop offset)
                      (take size)
                      (pull-entities db)
                      (map #(mappings/map-entity % schema)))]
    {"info"     {"size"    size
                 "first"   0
                 "prev"    (if (pos? page) (dec page) nil)
                 "current" page
                 "next"    (if (= page last) nil (inc page))
                 "last"    last}
     "entities" entities}))

(comment
  (let [database (first (utils/list-databases))]
    (time (datomic-resolve {:parent-type-name :EntityList
                            :field-name       :page
                            :parent-value     {:total 60 :page {:database database :t 5}}
                            :arguments        {:page 0
                                               :size 20}}))))

; TODO add reference and multi-reference resolver as bulk-resolvers
