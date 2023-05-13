(ns datomic.attributes
  (:require [clojure.test :refer [deftest is]]
            [datomic.client.api :as d]
            [datomic.temp :as temp]))

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
    :db/doc         "Description of any non-user platform entity."}
   {:db/ident       :platform/quantifications
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/doc         "Reference to quantifications of some platform entities."}])

(deftest platform-attributes-test
  (let [conn    (temp/conn)
        arg-map {:tx-data platform-attributes}
        tx1     (d/transact conn arg-map)
        tx2     (d/transact conn arg-map)]
    (is (< 1 (count (:tx-data tx1))))
    (is (= 1 (count (:tx-data tx2))))))

(defn doc [text]
  (str "Stored GraphQL Schema: " text))

(def graphql-attributes
  [{:db/ident       :collection/entities
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/doc         (doc "Entities which are part of this entity group, used for abstracting entities away from GraphQL types.")}
   {:db/ident       :graphql.type/deprecated?
    :db/valueType   :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db/doc         (doc "Whether this GraphQL type is deprecated or not. Default: `false`.")}
   {:db/ident       :graphql.type/name
    :db/unique      :db.unique/identity
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         (doc "Name of this GraphQL type.")}
   {:db/ident       :graphql.type/collection
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc         (doc "Collection used to access and group entities of this GraphQL type.")}
   {:db/ident       :graphql.type/fields
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/doc         (doc "Fields contained in this GraphQL type.")}
   {:db/ident       :graphql.field/deprecated?
    :db/valueType   :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db/doc         (doc "Whether this GraphQL field is deprecated. Default: `false`.")}
   {:db/ident       :graphql.field/name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         (doc "Name of this GraphQL field.")}
   {:db/ident       :graphql.field/target
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc         (doc "Type of this GraphQL fields target, iff it is a reference to another entity.")}
   {:db/ident       :graphql.field/attribute
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc         (doc "Datomic attribute which is pulled, when this GraphQL type and field is requested.")}
   {:db/ident       :graphql.field/backwards-ref?
    :db/valueType   :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db/doc         (doc "Whether the attribute should be pulled as backwards reference, when this GraphQL field is requested. Default: `false`.")}])

(deftest graphql-attributes-test
  (let [conn    (temp/conn)
        arg-map {:tx-data graphql-attributes}
        tx1     (d/transact conn arg-map)
        tx2     (d/transact conn arg-map)]
    (is (< 1 (count (:tx-data tx1))))
    (is (= 1 (count (:tx-data tx2))))))
