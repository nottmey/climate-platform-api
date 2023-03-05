(ns ions.mappings
  (:require
   [datomic.client.api :as d]
   [datomic.queries :as queries]
   [shared.attributes :as sa]
   [user :as u]))

(defn map-value [attribute db-value attribute-index]
  (let [{:keys [db/cardinality db/valueType]} (get attribute-index attribute)
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
  (let [db              (d/db (u/temp-conn))
        attribute-index (queries/get-attribute-index db)]
    #_(map-value :db/ident :db.part/db attribute-index)
    #_(map-value :db.install/partition [#:db{:id    0
                                             :ident :db.part/db}
                                        #:db{:id    3
                                             :ident :db.part/tx}
                                        #:db{:id    4
                                             :ident :db.part/user}] attribute-index)
    #_(map-value :db/doc "some docs" attribute-index)
    (map-value :graphql.relation/type+field [87960930222163 "name"] attribute-index)))

(defn map-entity [entity attribute-index]
  {"id"         (str (get entity :db/id))
   "attributes" (->> (dissoc entity :db/id)
                     (map
                      (fn [[a-key a-val]]
                        (merge
                         {"id"   (str (get-in attribute-index [a-key :db/id]))
                          "name" (str a-key)}
                         (map-value a-key a-val attribute-index)))))})

(comment
  (let [db              (d/db (u/temp-conn))
        attribute-index (queries/get-attribute-index db)
        result          (d/pull db '[*] 0)]
    (map-entity result attribute-index)))