(ns ions.resolvers
  (:require
   [clojure.string :as s]
   [datomic.client.api :as d]
   [datomic.queries :as queries]
   [ions.mappings :as mappings]
   [ions.utils :as utils]
   [shared.operations :as operations]
   [shared.operations.operation :as o]
   [user :as u]))

(def resolvable-paths (atom #{}))

(comment
  (seq @resolvable-paths))

(defmulti resolve-static-type-field
  (fn [{:keys [parent-type-name field-name]}]
    [parent-type-name field-name]))

(defn select-and-use-correct-resolver [{:keys [parent-type-name field-name]
                                        :as   args}]
  (if-let [op (->> (operations/all :datomic)
                   (filter #(= (o/get-graphql-parent-type %) parent-type-name))
                   (filter #(and (not (s/includes? (name field-name) "Entity"))
                                 (o/resolves-graphql-field? % field-name)))
                   first)]
    (o/resolve-field-data op args)
    (resolve-static-type-field args)))

(comment
  (let [args {:conn             (u/temp-conn)
              :parent-type-name :Query
              :field-name       :listPlanetaryBoundary}]
    (select-and-use-correct-resolver args))

  (let [args {:conn             (u/temp-conn)
              :parent-type-name :Query
              :field-name       :listEntity}]
    (select-and-use-correct-resolver args))

  (let [args {:conn             (u/temp-conn)
              :parent-type-name :Mutation
              :field-name       :createPlanetaryBoundary
              :arguments        {:value {:name "Climate Change"}}
              :selected-paths   #{"id" "name"}}]
    (select-and-use-correct-resolver args)))

(defmacro defresolver [multifn dispatch-val & fn-tail]
  (swap! resolvable-paths conj dispatch-val)
  `(defmethod ~multifn ~dispatch-val ~@fn-tail))

(defresolver resolve-static-type-field [:Query :getEntity] [{:keys [conn arguments]}]
  (let [{:keys [id]} arguments
        db              (d/db conn)
        attribute-index (queries/get-attribute-index db)
        result          (d/pull db '[*] (parse-long (str id)))]
    {:response (mappings/map-entity result attribute-index)}))

(comment
  (resolve-static-type-field {:conn             (u/temp-conn)
                              :parent-type-name :Query
                              :field-name       :getEntity
                              :arguments        {:id "0"}}))

(defn recently-updated-entities [db as]
  (->> (d/q '[:find ?e (max ?t)
              :in $ [?as ...]
              :where [?e ?as _ ?t]] db as)
       (sort-by second)
       (reverse)
       (map first)))

(defresolver resolve-static-type-field [:Query :listEntity] [{:keys [conn arguments]}]
  (let [{:keys [page filter]} arguments
        db              (d/db conn)
        attribute-index (queries/get-attribute-index db)
        as              (or (->> (:attributes filter) (map parse-long) seq)
                            (keys attribute-index))
        entities        (recently-updated-entities db as)
        page-info       (utils/page-info page (count entities))
        entities        (->> entities
                             (drop (get page-info "offset"))
                             (take (get page-info "size"))
                             (queries/pull-entities db '[*])
                             (map #(mappings/map-entity % attribute-index)))]
    {:response {"info"   page-info
                "values" entities}}))

(comment
  (time (resolve-static-type-field {:conn             (u/temp-conn)
                                    :parent-type-name :Query
                                    :field-name       :listEntity
                                    :arguments        {:filter {:attributes ["50" "77"]}
                                                       :page   {:number 0
                                                                :size   20}}})))

