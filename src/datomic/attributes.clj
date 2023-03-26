(ns datomic.attributes)

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

(def platform-attributes
  [{:db/ident       :platform/name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         "Name of any non-user platform entity."}])

(defn add-value-field-tx-data [type-name field-name attribute]
  [{:db/id             type-name
    :graphql.type/name type-name}
   {:graphql.relation/attribute attribute
    :graphql.relation/type      type-name
    :graphql.relation/field     field-name}])

; TODO test and use
#_(defn add-ref-field-tx-data [type-name field-name target-type attribute forward?]
    [{:db/id             type-name
      :graphql.type/name type-name}
     {:graphql.relation/attribute attribute
      :graphql.relation/forward?  forward?
      :graphql.relation/type      type-name
      :graphql.relation/field     field-name
      :graphql.relation/target    target-type}])

; TODO test
#_(defn deprecate-type-tx-data [type-name]
    [{:graphql.type/name        type-name
      :graphql.type/deprecated? true}])

; TODO test and use
#_(defn deprecate-type-field-tx-data [type-name field-name]
    ; FIXME doesn't work like this, always creates new relation entities
    [{:db/id             type-name
      :graphql.type/name type-name}
     {:graphql.relation/type+field  [type-name field-name]
      :graphql.relation/deprecated? true}])
