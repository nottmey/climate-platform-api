(ns datomic.schema
  (:require [datomic.client.api :as d]
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
  (u/ensure-schema graphql-attributes))

(def platform-attributes
  [{:db/ident       :platform/name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         "Name of any non-user platform entity."}])

(comment
  (u/ensure-schema platform-attributes))

(defn add-value-field-tx-data [type-name field-name attribute]
  [{:db/id             type-name
    :graphql.type/name type-name}
   {:graphql.relation/attribute attribute
    :graphql.relation/type      type-name
    :graphql.relation/field     field-name}])

(comment
  (u/ensure-data
    (add-value-field-tx-data "SomeTypeY" "ident" :db/ident)))

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
  [{:db/id             type-name
    :graphql.type/name type-name}
   {:graphql.relation/type+field  [type-name field-name]
    :graphql.relation/deprecated? true}])

(comment
  (d/transact
    (u/sandbox-conn)
    {:tx-data (deprecate-type-field-tx-data "SomeType" "refToX")}))

(defn get-all-type-fields [db]
  (d/q '[:find ?type-name ?field-name
         :where
         [?type :graphql.type/name ?type-name]
         [?rel :graphql.relation/type ?type]
         [?rel :graphql.relation/field ?field-name]]
       db))

(comment
  (let [db (u/sandbox-db)]
    (time (get-all-type-fields db))))

(defn get-relations [db type-field-tuples]
  (d/q '[:find (pull ?rel [*])
         :in $ [[?type-name ?field-name]]
         :where
         [?type :graphql.type/name ?type-name]
         [(tuple ?type ?field-name) ?tup]
         [?rel :graphql.relation/type+field ?tup]]
       db
       type-field-tuples))

(comment
  (let [db     (u/sandbox-db)
        tuples (get-all-type-fields db)]
    (time (get-relations db tuples))))