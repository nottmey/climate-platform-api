(ns datomic.attributes
  (:require [clojure.test :refer [deftest is]]
            [datomic.client.api :as d])
  (:import (clojure.lang ExceptionInfo)))

; TODO use transaction function to ensure type is complete

(defn- temp-conn []
  (let [db-name "testing"
        client  (d/client {:server-type :dev-local
                           :storage-dir :mem
                           :system      db-name})
        arg-map {:db-name db-name}]
    (d/delete-database client arg-map)
    (d/create-database client arg-map)
    (d/connect client arg-map)))

(def platform-attributes
  [{:db/ident       :platform/id
    :db/valueType   :db.type/uuid
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity
    :db/doc         "Client generated external UUID for any non-user platform entity.

                     It allows clients to have a simpler structure, because they can
                     assume that they have a correct id before creating an entity."}
   {:db/ident       :platform/name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         "Name of any non-user platform entity."}
   {:db/ident       :platform/description
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         "Description of any non-user platform entity."}])

(deftest platform-attributes-test
  (let [conn    (temp-conn)
        arg-map {:tx-data platform-attributes}
        tx1     (d/transact conn arg-map)
        tx2     (d/transact conn arg-map)]
    (is (< 1 (count (:tx-data tx1))))
    (is (= 1 (count (:tx-data tx2))))))

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

(deftest graphql-attributes-test
  (let [conn    (temp-conn)
        arg-map {:tx-data graphql-attributes}
        tx1     (d/transact conn arg-map)
        tx2     (d/transact conn arg-map)]
    (is (< 1 (count (:tx-data tx1))))
    (is (= 1 (count (:tx-data tx2))))))

(defn add-type-tx-data [temp-id type-name]
  [{:db/id             temp-id
    :graphql.type/name type-name}])

(deftest add-type-tx-data-test
  (let [conn    (temp-conn)
        _       (d/transact conn {:tx-data graphql-attributes})
        arg-map {:tx-data (add-type-tx-data "tempid" "SomeType")}
        tx1     (d/transact conn arg-map)
        tx2     (d/transact conn arg-map)
        query   (d/q '[:find ?v :where [?e :graphql.type/name ?v]] (d/db conn))]
    (is (< 1 (count (:tx-data tx1))))
    (is (= 1 (count (:tx-data tx2))))
    (is (= [["SomeType"]] query))))

(defn add-value-field-tx-data [temp-id type-name field-name attribute]
  [{:db/id                      temp-id
    :graphql.relation/attribute attribute
    :graphql.relation/type      [:graphql.type/name type-name]
    :graphql.relation/field     field-name}])

(declare thrown-with-msg?)
(deftest add-value-field-tx-data-test
  (let [conn    (temp-conn)
        _       (d/transact conn {:tx-data graphql-attributes})
        _       (d/transact conn {:tx-data [{:db/ident       :some/attribute
                                             :db/valueType   :db.type/string
                                             :db/cardinality :db.cardinality/one}]})
        _       (d/transact conn {:tx-data (add-type-tx-data "tempid" "SomeType")})
        arg-map {:tx-data (add-value-field-tx-data "tempid" "SomeType" "someField" :some/attribute)}
        tx      (d/transact conn arg-map)
        query   (d/q '[:find ?type-name ?field-value ?attr-value
                       :where
                       [?rel :graphql.relation/type ?type] [?type :graphql.type/name ?type-name]
                       [?rel :graphql.relation/field ?field-value]
                       [?rel :graphql.relation/attribute ?attr] [?attr :db/ident ?attr-value]]
                     (d/db conn))]
    (is (thrown-with-msg?
         ExceptionInfo
         #"unique-conflict"
         (d/transact conn arg-map)))
    (is (< 1 (count (:tx-data tx))))
    (is (= [["SomeType" "someField" :some/attribute]]
           query))))

(defn add-ref-field-tx-data [temp-id type-name field-name attribute target-type-name forward?]
  [{:db/id                      temp-id
    :graphql.relation/attribute attribute
    :graphql.relation/type      [:graphql.type/name type-name]
    :graphql.relation/field     field-name
    :graphql.relation/target    [:graphql.type/name target-type-name]
    :graphql.relation/forward?  forward?}])

(deftest add-ref-field-tx-data-test
  (let [conn    (temp-conn)
        _       (d/transact conn {:tx-data graphql-attributes})
        _       (d/transact conn {:tx-data [{:db/ident       :some/ref
                                             :db/valueType   :db.type/ref
                                             :db/cardinality :db.cardinality/many}]})
        _       (d/transact conn {:tx-data (add-type-tx-data "tempid" "SomeType")})
        arg-map {:tx-data (add-ref-field-tx-data "tempid" "SomeType" "someField" :some/ref "SomeType" true)}
        tx      (d/transact conn arg-map)
        query   (d/q '[:find ?type-name ?field-value ?attr-value ?target-name ?forward-value
                       :where
                       [?rel :graphql.relation/type ?type] [?type :graphql.type/name ?type-name]
                       [?rel :graphql.relation/field ?field-value]
                       [?rel :graphql.relation/attribute ?attr] [?attr :db/ident ?attr-value]
                       [?rel :graphql.relation/target ?target] [?target :graphql.type/name ?target-name]
                       [?rel :graphql.relation/forward? ?forward-value]]
                     (d/db conn))]
    (is (thrown-with-msg?
         ExceptionInfo
         #"unique-conflict"
         (d/transact conn arg-map)))
    (is (< 1 (count (:tx-data tx))))
    (is (= [["SomeType" "someField" :some/ref "SomeType" true]]
           query))))

(defn deprecate-type-tx-data [type-name]
  [{:db/id                    [:graphql.type/name type-name]
    :graphql.type/deprecated? true}])

(deftest deprecate-type-tx-data-test
  (let [conn  (temp-conn)
        _     (d/transact conn {:tx-data graphql-attributes})
        _     (d/transact conn {:tx-data (add-type-tx-data "tempid" "SomeType")})
        tx1   (d/transact conn {:tx-data (deprecate-type-tx-data "SomeType")})
        tx2   (d/transact conn {:tx-data (deprecate-type-tx-data "SomeType")})
        query (d/q '[:find ?type-name ?deprecated-value
                     :where
                     [?type :graphql.type/name ?type-name]
                     [?type :graphql.type/deprecated? ?deprecated-value]]
                   (d/db conn))]
    (is (thrown-with-msg?
         ExceptionInfo
         #"not-an-entity"
         (d/transact conn {:tx-data (deprecate-type-tx-data "X")})))
    (is (< 1 (count (:tx-data tx1))))
    (is (= 1 (count (:tx-data tx2))))
    (is (= [["SomeType" true]] query))))

(defn deprecate-type-field-tx-data [type-id field-name]
  [{:db/id                        [:graphql.relation/type+field [type-id field-name]]
    :graphql.relation/deprecated? true}])

(deftest deprecate-type-field-tx-data-test
  (let [conn    (temp-conn)
        _       (d/transact conn {:tx-data graphql-attributes})
        _       (d/transact conn {:tx-data [{:db/ident       :some/attribute
                                             :db/valueType   :db.type/string
                                             :db/cardinality :db.cardinality/one}]})
        type-tx (d/transact conn {:tx-data (add-type-tx-data "tempid" "SomeType")})
        type-id (get-in type-tx [:tempids "tempid"])
        _       (d/transact conn {:tx-data (add-value-field-tx-data "tempid" "SomeType" "someField" :some/attribute)})
        arg-map {:tx-data (deprecate-type-field-tx-data type-id "someField")}
        tx1     (d/transact conn arg-map)
        tx2     (d/transact conn arg-map)
        query   (d/q '[:find ?type-name ?field-value ?attr-value ?deprecated-value
                       :where
                       [?rel :graphql.relation/type ?type] [?type :graphql.type/name ?type-name]
                       [?rel :graphql.relation/field ?field-value]
                       [?rel :graphql.relation/attribute ?attr] [?attr :db/ident ?attr-value]
                       [?rel :graphql.relation/deprecated? ?deprecated-value]]
                     (d/db conn))]
    (is (thrown-with-msg?
         ExceptionInfo
         #"not-an-entity"
         (d/transact conn {:tx-data (deprecate-type-field-tx-data 0 "someField")})))
    (is (thrown-with-msg?
         ExceptionInfo
         #"not-an-entity"
         (d/transact conn {:tx-data (deprecate-type-field-tx-data type-id "unknownField")})))
    (is (< 1 (count (:tx-data tx1))))
    (is (= 1 (count (:tx-data tx2))))
    (is (= [["SomeType" "someField" :some/attribute true]]
           query))))
