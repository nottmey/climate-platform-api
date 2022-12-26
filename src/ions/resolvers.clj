(ns ions.resolvers
  (:require [datomic.client.api :as d]
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
     "slice" {"database" database}}))

(comment
  (datomic-resolve {:parent-type-name :Query
                    :field-name       :list
                    :arguments        {:database (first (utils/list-databases))}}))

(defresolver datomic-resolve [:EntityList :slice] [{:keys [arguments source]}]
  (let [{:keys [limit offset]} arguments
        #_{:keys [database]} #_source
        database    "datomic-docs-tutorial"
        offset      (max 0 (or offset 0))
        limit       (min 100 (max 1 (or limit 100)))
        db          (d/db (utils/get-connection database))
        schema      (utils/get-schema db)
        db-entities (db-entities db schema)
        entities    (->> db-entities
                         (drop offset)
                         (take limit)
                         (map first)
                         (map #(mappings/map-entity % schema)))]
    {"usedLimit"  limit
     "usedOffset" offset
     "entities"   entities}))

(comment
  (let [database (first (utils/list-databases))]
    (time (datomic-resolve {:parent-type-name :EntityList
                            :field-name       :slice
                            :source           {:database database}
                            :arguments        {:limit  10
                                               :offset 0}}))))