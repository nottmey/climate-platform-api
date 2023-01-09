(ns datomic.schema
  (:require [datomic.client.api :as d]
            [datomic.access :as a]))

; todo use transaction function to ensure type is complete
(def graphql-attributes
  [{:db/ident       :graphql.type/name
    :db/unique      :db.unique/identity
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident       :graphql.type/deprecated?
    :db/valueType   :db.type/boolean
    :db/cardinality :db.cardinality/one}
   ;; relation
   ; deprecated?
   {:db/ident       :graphql.relation/deprecated?
    :db/valueType   :db.type/boolean
    :db/cardinality :db.cardinality/one}
   ; source-type
   {:db/ident       :graphql.relation/type
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one}
   ; forward-field
   {:db/ident       :graphql.relation/field
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   ; type+field
   {:db/ident       :graphql.relation/type+field
    :db/unique      :db.unique/identity
    :db/valueType   :db.type/tuple
    :db/tupleAttrs  [:graphql.relation/type
                     :graphql.relation/field]
    :db/cardinality :db.cardinality/one}
   ; destination-type, optional
   {:db/ident       :graphql.relation/target
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one}
   ; attribute
   {:db/ident       :graphql.relation/attribute
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one}
   ; forward?
   {:db/ident       :graphql.relation/forward?
    :db/valueType   :db.type/boolean
    :db/cardinality :db.cardinality/one}])

(comment
  ; add some data
  (let [conn (a/get-connection "sandbox")]
    (d/transact conn {:tx-data
                      [{:db/id                    "SomeType"
                        :graphql.type/name        "SomeType"
                        :graphql.type/deprecated? false}
                       {:graphql.relation/attribute   :db/ident
                        :graphql.relation/forward?    true
                        :graphql.relation/type        "SomeType"
                        :graphql.relation/deprecated? false
                        :graphql.relation/field       "testRelationTo"
                        :graphql.relation/target      "SomeType"}]}))
  ; find relation for query
  (let [db (d/db (a/get-connection "sandbox"))]
    (time (d/q '[:find (pull ?rel [*])
                 :in $ ?type-name [?field-name ...]
                 :where
                 [?type :graphql.type/name ?type-name]
                 [(tuple ?type ?field-name) ?tup]
                 [?rel :graphql.relation/type+field ?tup]]
               db
               "SomeType"
               ["testRelationTo"])))

  ; generate all types with attributes
  (let [db (d/db (a/get-connection "sandbox"))]
    (time (d/q '[:find ?type-name ?field-name
                 :where
                 [?type :graphql.type/name ?type-name]
                 [?rel :graphql.relation/type ?type]
                 [?rel :graphql.relation/field ?field-name]]
               db))))