(ns ions.mappings
  (:require
   [datomic.access :as da]
   [datomic.client.api :as d]
   [shared.attributes :as sa]))

(defn map-value [attribute db-value schema]
  (let [{:keys [db/cardinality db/valueType]} (get schema attribute)
        many?       (= cardinality :db.cardinality/many)
        type-config (->> sa/attribute-types
                         (filter
                          (fn [{:keys [datomic/type]}]
                            (contains? type valueType)))
                         first)]
    (assert (some? type-config) (str "There is a GraphQL type configured for value type " valueType "."))
    (let [{:keys [graphql/multi-value-field-name
                  graphql/single-value-field-name
                  graphql/multi-value-type-name
                  graphql/single-value-type-name
                  datomic/->gql]} type-config
          gql-type-name (if many? multi-value-type-name single-value-type-name)
          gql-key       (if many? multi-value-field-name single-value-field-name)
          gql-value     (if many? (map ->gql db-value) (->gql db-value))]
      {"__typename"   (name gql-type-name)
       (name gql-key) gql-value})))

(comment
  (let [db     (d/db (da/get-connection da/dev-env-db-name))
        schema (da/get-schema db)]
    #_(map-value :db/ident :db.part/db schema)
    #_(map-value :db.install/partition [#:db{:id 0,
                                             :ident :db.part/db}
                                        #:db{:id 3,
                                             :ident :db.part/tx}
                                        #:db{:id 4,
                                             :ident :db.part/user}] schema)
    #_(map-value :db/doc "some docs" schema)
    (map-value :graphql.relation/type+field [87960930222163 "name"] schema)))

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
  (let [db     (d/db (da/get-connection (first (da/list-databases))))
        schema (da/get-schema db)
        result (d/pull db '[*] 21)]
    (map-entity result schema)))