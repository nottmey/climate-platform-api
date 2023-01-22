(ns datomic.schema
  (:require [datomic.client.api :as d]
            [shared.attributes :as a]
            [user :as u]))

; TODO use transaction function to ensure type is complete

(defn graphql-doc [text]
  (str "Stored GraphQL Schema: " text))

(def graphql-attributes
  [{:db/ident       :graphql.type/name
    :db/unique      :db.unique/identity
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         (graphql-doc "Name of this GraphQL type.")}
   {:db/ident       :graphql.type/deprecated?
    :db/valueType   :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db/doc         (graphql-doc "Whether this GraphQL type is deprecated or not. Default: `false`.")}
   {:db/ident       :graphql.relation/deprecated?
    :db/valueType   :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db/doc         (graphql-doc "Whether the GraphQL field of this GraphQL-field-to-Datomic-attribute relation is deprecated or not. Default: `false`.")}
   {:db/ident       :graphql.relation/type
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc         (graphql-doc "GraphQL type in which the GraphQL field of this GraphQL-field-to-Datomic-attribute relation is contained.")}
   {:db/ident       :graphql.relation/field
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         (graphql-doc "Field name of this GraphQL-field-to-Datomic-attribute relation.")}
   {:db/ident       :graphql.relation/type+field
    :db/unique      :db.unique/identity
    :db/valueType   :db.type/tuple
    :db/tupleAttrs  [:graphql.relation/type
                     :graphql.relation/field]
    :db/cardinality :db.cardinality/one
    :db/doc         (graphql-doc "Unique automatically managed GraphQL-type-and-field-name tuple of this GraphQL-field-to-Datomic-attribute relation.")}
   {:db/ident       :graphql.relation/target
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc         (graphql-doc "GraphQL type which is targeted by this GraphQL-field-to-Datomic-attribute relation, iff it is a reference to another entity.")}
   {:db/ident       :graphql.relation/attribute
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc         (graphql-doc "Datomic attribute which is pulled, when this relations type and field is requested in the GraphQL API.")}
   {:db/ident       :graphql.relation/forward?
    :db/valueType   :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db/doc         (graphql-doc "Whether the forward or backwards reference of this relations reference attribute should be pulled, when requested in the GraphQL API. Default: `true`.")}])

(comment
  (u/ensure-schema
    graphql-attributes
    #_access/dev-env-db-name))

(def platform-attributes
  [{:db/ident       :platform/name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         "Name of any non-user platform entity."}])

(comment
  (u/ensure-schema
    platform-attributes
    #_access/dev-env-db-name))

(defn add-value-field-tx-data [type-name field-name attribute]
  [{:db/id             type-name
    :graphql.type/name type-name}
   {:graphql.relation/attribute attribute
    :graphql.relation/type      type-name
    :graphql.relation/field     field-name}])

(comment
  (u/ensure-data
    (add-value-field-tx-data "PlanetaryBoundary" "name" :platform/name)
    #_access/dev-env-db-name))

(defn add-ref-field-tx-data [type-name field-name target-type attribute forward?]
  [{:db/id             type-name
    :graphql.type/name type-name}
   {:graphql.relation/attribute attribute
    :graphql.relation/forward?  forward?
    :graphql.relation/type      type-name
    :graphql.relation/field     field-name
    :graphql.relation/target    target-type}])

(comment
  (d/transact
    (u/sandbox-conn)
    {:tx-data (add-ref-field-tx-data "SomeType" "refToX" "SomeType" :db/cardinality true)}))

(defn deprecate-type-tx-data [type-name]
  [{:graphql.type/name        type-name
    :graphql.type/deprecated? true}])

(comment
  (d/transact
    (u/sandbox-conn)
    {:tx-data (deprecate-type-tx-data "SomeType")}))

(defn deprecate-type-field-tx-data [type-name field-name]
  ; FIXME doesn't work like this, always creates new relation entities
  [{:db/id             type-name
    :graphql.type/name type-name}
   {:graphql.relation/type+field  [type-name field-name]
    :graphql.relation/deprecated? true}])

(comment
  (d/transact
    (u/sandbox-conn)
    {:tx-data (deprecate-type-field-tx-data "SomeType" "refToX")}))

(defn get-all-entity-fields [db]
  (d/q '[:find ?type-name ?field-name ?value-type-ident ?cardinality-ident
         :where
         [?type :graphql.type/name ?type-name]
         [?rel :graphql.relation/type ?type]
         [?rel :graphql.relation/field ?field-name]
         [?rel :graphql.relation/attribute ?attr]
         [?attr :db/valueType ?value-type]
         [?value-type :db/ident ?value-type-ident]
         [?attr :db/cardinality ?cardinality]
         [?cardinality :db/ident ?cardinality-ident]]
       db))

(comment
  (let [db (u/sandbox-db)]
    (time (get-all-entity-fields db))))

(defn get-all-values [db type-field-tuples]
  (d/q '[:find (pull ?rel [*])
         :in $ [[?type-name ?field-name]]
         :where
         [?type :graphql.type/name ?type-name]
         [(tuple ?type ?field-name) ?tup]
         [?rel :graphql.relation/type+field ?tup]]
       db
       type-field-tuples))

(defn get-specific-values [db type-entity-field-triples]
  (-> (->> (d/q '[:find ?type-name ?e ?field-name ?vt-ident ?v
                  :in $ [[?type-name ?e ?field-name]]
                  :where
                  [?type :graphql.type/name ?type-name]
                  [(tuple ?type ?field-name) ?tup]
                  [?rel :graphql.relation/type+field ?tup]
                  [?rel :graphql.relation/attribute ?a]
                  [?a :db/valueType ?vt]
                  [?vt :db/ident ?vt-ident]
                  [?e ?a ?v]]
                db
                type-entity-field-triples)
           (group-by first))
      (update-vals
        (fn [type-values]
          (-> (->> type-values
                   (map rest)
                   (group-by first))
              (update-vals
                (fn [entity-values]
                  (-> (->> entity-values
                           (map
                             (fn [[_ field-name value-type value]]
                               [field-name ((a/datomic-value-to-gql-value-fn value-type) value)]))
                           (into {}))
                      (assoc "id" (first (first entity-values)))))))))))

(comment
  (let [db (u/sandbox-db)]
    (time (get-specific-values db [["PlanetaryBoundary" 92358976733295 "name"]])))

  (let [db (u/sandbox-db)]
    (time (get-specific-values db
                               [["SomeTypeY" 0 "identX"]
                                ["SomeType" 1 "ident"]
                                ["SomeTypeY" 2 "identX"]
                                ["SomeTypeY" 5 "identX"]
                                ["SomeType" 12 "ident"]]))))