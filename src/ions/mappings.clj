(ns ions.mappings
  (:require [datomic.client.api :as d]
            [ions.utils :as utils]
            [shared.attributes :as sa]))

(defn map-value [attribute db-value schema]
  (let [{:keys [db/valueType db/cardinality]} (get schema attribute)
        many?     (= cardinality :db.cardinality/many)
        gql-key   (if many? "values" "value")
        db->gql   (->> sa/attribute-types
                       (filter
                         (fn [{:keys [datomic/type]}]
                           (contains? type valueType)))
                       first
                       :db->gql)
        gql-value (if many? (map db->gql db-value) (db->gql db-value))]
    {gql-key gql-value}))

(comment
  (let [db     (d/db (utils/get-connection (first (utils/list-databases))))
        schema (utils/get-schema db)]
    #_(map-value :db/ident :db.part/db schema)
    #_(map-value :db.install/partition [#:db{:id 0, :ident :db.part/db}
                                        #:db{:id 3, :ident :db.part/tx}
                                        #:db{:id 4, :ident :db.part/user}] schema)
    (map-value :db/doc "some docs" schema)))

(defn map-entity [entity schema]
  {"id"         (str (get entity :db/id))
   "attributes" (->> (dissoc entity :db/id)
                     (map
                       (fn [[a-key a-val]]
                         (merge
                           {"id"   (str (get-in schema [a-key :db/id]))
                            "name" (str a-key)}
                           (map-value a-key a-val schema)))))})

(comment
  (let [db     (d/db (utils/get-connection (first (utils/list-databases))))
        schema (utils/get-schema db)
        result (d/pull db '[*] 21)]
    (map-entity result schema)))