(ns ions.resolvers
  (:require [datomic.client.api :as d]
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
        result (d/pull db '[*] (parse-long (str id)))]
    (hash-map "id" (get result :db/id)
              "data" (str result))))

(comment
  (datomic-resolve {:parent-type-name :Query
                    :field-name       :get
                    :arguments        {:database (first (utils/list-databases))
                                       :id       "0"}}))

(defn as-vec [x]
  (if (sequential? x)
    x
    (vector x)))

(defresolver datomic-resolve [:Query :list] [{:keys [arguments]}]
  (let [{:keys [database offset limit]} arguments
        db     (d/db (utils/get-connection database))
        offset (max 0 (or offset 0))
        limit  (min 100 (max 1 (or limit 100)))
        schema (utils/get-schema db)
        es     (->> (d/datoms db {:index :eavt})
                    (map :e)
                    (distinct)
                    (drop offset)
                    (take limit))]
    (->> (d/q {:query  '[:find (pull ?es [*]) :in $ [?es ...]]
               :args   [db es]
               :offset offset
               :limit  limit})
         (map first)
         (map (fn [e]
                {"id"         (str (get e :db/id))
                 "attributes" (->> (dissoc e :db/id)
                                   (map (fn [[k v]]
                                          {"id"     (str (get-in schema [k :db/id]))
                                           "name"   (str k)
                                           "type"   (name (get-in schema [k :db/valueType]))
                                           "values" (->> (as-vec v)
                                                         (map str))})))})))))

(comment
  (datomic-resolve {:parent-type-name :Query
                    :field-name       :list
                    :arguments        {:database (first (utils/list-databases))
                                       :limit    100
                                       :offset   0}}))