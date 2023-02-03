(ns ions.resolvers
  (:require
    [clojure.string :as s]
    [datomic.access :as da]
    [datomic.client.api :as d]
    [ions.mappings :as mappings]
    [ions.utils :as utils]
    [shared.operations :as ops]
    [shared.operations.operation :as o]))

(defn- extract-type-field-tuple [{:keys [parent-type-name field-name]}]
  [parent-type-name field-name])

(def resolvable-paths (atom #{}))

(comment
  (seq @resolvable-paths))

(defmulti resolve-static-type-field extract-type-field-tuple)

(defn select-and-use-correct-resolver [{:keys [parent-type-name field-name] :as args}]
  (if-let [op (->> (ops/all)
                   (filter #(= (o/get-graphql-parent-type %) parent-type-name))
                   (filter #(and (not (s/includes? (name field-name) "Entity"))
                                 (o/resolves-graphql-field? % field-name)))
                   first)]
    (let [conn (da/get-connection da/dev-env-db-name)]
      (o/resolve-field-data op conn args))
    (resolve-static-type-field args)))

(comment
  (let [args {:parent-type-name :Query
              :field-name       :listPlanetaryBoundary}]
    (time (select-and-use-correct-resolver args)))

  (let [args {:parent-type-name :Query
              :field-name       :listEntity}]
    (time (select-and-use-correct-resolver args))))

(defmacro defresolver [multifn dispatch-val & fn-tail]
  (swap! resolvable-paths conj dispatch-val)
  `(defmethod ~multifn ~dispatch-val ~@fn-tail))

(defresolver resolve-static-type-field [:Query :getEntity] [{:keys [arguments]}]
  (let [{:keys [id]} arguments
        db     (d/db (da/get-connection da/dev-env-db-name))
        schema (da/get-schema db)
        result (d/pull db '[*] (parse-long (str id)))]
    (mappings/map-entity result schema)))

(comment
  (resolve-static-type-field {:parent-type-name :Query
                              :field-name       :getEntity
                              :arguments        {:id "0"}}))

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
  (let [db     (d/db (da/get-connection da/dev-env-db-name))
        schema (da/get-schema db)
        es     (time (recently-updated-entities db #_["50"] (keys schema)))]
    (time (doall (pull-entities db es)))))

(defresolver resolve-static-type-field [:Query :listEntity] [{:keys [arguments]}]
  (let [{:keys [page filter]} arguments
        db        (d/db (da/get-connection da/dev-env-db-name))
        schema    (da/get-schema db)
        as        (or (->> (:attributes filter) (map parse-long) seq)
                      (keys schema))
        entities  (recently-updated-entities db as)
        page-info (utils/page-info page (count entities))
        entities  (->> entities
                       (drop (get page-info "offset"))
                       (take (get page-info "size"))
                       (pull-entities db)
                       (map #(mappings/map-entity % schema)))]
    {"info"   page-info
     "values" entities}))

(comment
  (time (resolve-static-type-field {:parent-type-name :Query
                                    :field-name       :listEntity
                                    :arguments        {:filter {:attributes ["50" "77"]}
                                                       :page   {:number 0 :size 20}}})))

