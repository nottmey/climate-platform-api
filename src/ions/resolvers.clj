(ns ions.resolvers
  (:require [datomic.access :as da]
            [datomic.client.api :as d]
            [ions.mappings :as mappings]))

(defn- extract-type-field-tuple [{:keys [parent-type-name field-name]}]
  [parent-type-name field-name])

(def resolvable-paths (atom #{}))

(comment
  (seq @resolvable-paths))

(defmulti resolve-static-type-field extract-type-field-tuple)

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
        {:keys [number size]} page
        db          (d/db (da/get-connection da/dev-env-db-name))
        schema      (da/get-schema db)
        as          (or (->> (:attributes filter) (map parse-long) seq)
                        (keys schema))
        entities    (recently-updated-entities db as)
        total       (count entities)
        size        (min 100 (max 1 (or size 20)))
        last        (if (pos? total)
                      (dec (int (Math/ceil (/ total size))))
                      0)
        page-number (min last (max 0 (or number 0)))
        offset      (* page-number size)
        entities    (->> entities
                         (drop offset)
                         (take size)
                         (pull-entities db)
                         (map #(mappings/map-entity % schema)))]
    {"info"   {"size"    size
               "first"   0
               "prev"    (if (pos? page-number) (dec page-number) nil)
               "current" page-number
               "next"    (if (= page-number last) nil (inc page-number))
               "last"    last}
     "values" entities}))

(comment
  (time (resolve-static-type-field {:parent-type-name :Query
                                    :field-name       :listEntity
                                    :arguments        {:filter {:attributes ["50" "77"]}
                                                       :page   {:number 0 :size 20}}})))

