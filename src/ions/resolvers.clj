(ns ions.resolvers
  (:require
   [clojure.string :as str]
   [datomic.client.api :as d]
   [datomic.queries :as q]
   [ions.utils :as utils]
   [shared.mappings :as mappings]
   [shared.operations :as ops]
   [user :as u]))

(def resolvable-paths (atom #{}))

(comment
  (seq @resolvable-paths))

(defmulti resolve-static
  (fn [{:keys [parent-type-name field-name]}]
    [parent-type-name field-name]))

(defn select-and-use-resolver [{:keys [parent-type-name field-name]
                                :as   args}]
  (let [op (ops/select-datomic-field-operation parent-type-name field-name)]
    (if (and (some? op)
             (not (str/includes? (name field-name) "Entity")))
      (ops/resolve-dynamic op args)
      (resolve-static args))))

(comment
  (let [args {:conn             (u/temp-conn)
              :parent-type-name :Query
              :field-name       :listPlanetaryBoundary}]
    (select-and-use-resolver args))

  (let [args {:conn             (u/temp-conn)
              :parent-type-name :Query
              :field-name       :listEntity}]
    (select-and-use-resolver args))

  (let [args {:conn             (u/temp-conn)
              :parent-type-name :Mutation
              :field-name       :createPlanetaryBoundary
              :arguments        {:value {:name "Climate Change"}}
              :selected-paths   #{"id" "name"}}]
    (select-and-use-resolver args)))

(defmacro defresolver [multifn dispatch-val & fn-tail]
  (swap! resolvable-paths conj dispatch-val)
  `(defmethod ~multifn ~dispatch-val ~@fn-tail))

(defresolver resolve-static [:Query :getEntity] [{:keys [conn arguments]}]
  (let [{:keys [id]} arguments
        db              (d/db conn)
        attribute-index (q/get-attribute-index db)
        result          (d/pull db '[*] (parse-long (str id)))]
    {:response (mappings/map-entity result attribute-index)}))

(comment
  (resolve-static {:conn             (u/temp-conn)
                   :parent-type-name :Query
                   :field-name       :getEntity
                   :arguments        {:id "0"}}))

(defresolver resolve-static [:Query :listEntity] [{:keys [conn arguments]}]
  (let [{:keys [page filter]} arguments
        db              (d/db conn)
        attribute-index (q/get-attribute-index db)
        as              (or (->> (:attributes filter) (map parse-long) seq)
                            (keys attribute-index))
        entities        (q/recently-updated-entities db as)
        page-info       (utils/page-info page (count entities))
        entities        (->> entities
                             (drop (get page-info "offset"))
                             (take (get page-info "size"))
                             (q/pull-entities db '[*])
                             (map #(mappings/map-entity % attribute-index)))]
    {:response {"info"   page-info
                "values" entities}}))

(comment
  (time (resolve-static {:conn             (u/temp-conn)
                         :parent-type-name :Query
                         :field-name       :listEntity
                         :arguments        {#_#_:filter {:attributes ["50" "77"]}
                                            #_#_:page {:number 0
                                                       :size   20}}})))

