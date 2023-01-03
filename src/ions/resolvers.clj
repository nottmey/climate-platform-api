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

(defn db-entities [db schema]
  (d/qseq {:query '[:find (pull ?e [*])
                    :in $ [?as ...]
                    :where [?e ?as]]
           :args  [db (keys schema)]}))

(defresolver datomic-resolve [:Query :list] [{:keys [arguments]}]
  (let [{:keys [database]} arguments
        db          (d/db (utils/get-connection database))
        schema      (utils/get-schema db)
        db-entities (db-entities db schema)]
    {"total" (count db-entities)
     ; handing down context args to the field resolver (needed because parameterizable)
     "page"  {"database" database}}))

(comment
  (datomic-resolve {:parent-type-name :Query
                    :field-name       :list
                    :arguments        {:database (first (utils/list-databases))}}))

(defresolver datomic-resolve [:EntityList :page] [{:keys [arguments parent-value]}]
  (let [{:keys [page size]} arguments
        database    (get-in parent-value [:page :database])
        db          (d/db (utils/get-connection database))
        schema      (utils/get-schema db)
        db-entities (db-entities db schema)
        total       (count db-entities)
        size        (min 100 (max 1 (or size 20)))
        last        (if (pos? total)
                      (dec (int (Math/ceil (/ total size))))
                      0)
        page        (min last (max 0 (or page 0)))
        offset      (* page size)
        entities    (->> db-entities
                         (drop offset)
                         (take size)
                         (map first)
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
                            :parent-value     {:total 60 :page {:database database}}
                            :arguments        {:page 0
                                               :size 20}}))))