(ns ions.resolvers
  (:require [clojure.walk :as walk]
            [datomic.access :as da]
            [datomic.client.api :as d]
            [ions.mappings :as mappings]))

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
  (da/list-databases))

(comment
  (datomic-resolve {:parent-type-name :Query :field-name :databases}))

(defresolver datomic-resolve [:Query :get] [{:keys [arguments]}]
  (let [{:keys [database id]} arguments
        db     (d/db (da/get-connection database))
        schema (da/get-schema db)
        result (d/pull db '[*] (parse-long (str id)))]
    (mappings/map-entity result schema)))

(comment
  (datomic-resolve {:parent-type-name :Query
                    :field-name       :get
                    :arguments        {:database (first (da/list-databases))
                                       :id       "0"}}))

(defn recently-updated-entities [db as]
  (->> (d/q '[:find ?e (max ?t)
              :in $ [?as ...]
              :where [?e ?as _ ?t]] db as)
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
  (let [db     (d/db (da/get-connection (first (da/list-databases))))
        schema (da/get-schema db)
        es     (time (recently-updated-entities db #_["50"] (keys schema)))]
    (time (doall (pull-entities db es)))))

(defresolver datomic-resolve [:Query :list] [{:keys [arguments]}]
  (let [{:keys [database filter]} arguments
        db (d/db (da/get-connection database))
        as (or (->> (:attributes filter) (map parse-long) seq)
               (keys (da/get-schema db)))]
    {"total" (count (recently-updated-entities db as))
     ; handing down context args to the field resolver (needed because parameterizable)
     "page"  {"database" database
              "t"        (:t db)
              "filter"   (walk/stringify-keys filter)}}))

(comment
  (datomic-resolve {:parent-type-name :Query
                    :field-name       :list
                    :arguments        {:database (first (da/list-databases))
                                       :filter   {:attributes ["50"]}}}))

(defresolver datomic-resolve [:EntityList :page] [{:keys [arguments parent-value]}]
  (let [{:keys [page size]} arguments
        {{:keys [database t filter]} :page} parent-value
        db       (d/as-of (d/db (da/get-connection database)) t)
        schema   (da/get-schema db)
        as       (or (->> (:attributes filter) (map parse-long) seq)
                     (keys schema))
        entities (recently-updated-entities db as)
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
  (let [database (first (da/list-databases))]
    (time (datomic-resolve {:parent-type-name :EntityList
                            :field-name       :page
                            :parent-value     {:total 5 :page {:database database
                                                               :t        5
                                                               :filter   {:attributes ["50"]}}}
                            :arguments        {:page 0
                                               :size 20}}))))

; TODO add reference and multi-reference resolver as bulk-resolvers
