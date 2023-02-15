(ns shared.operations.get
  (:require
   [clojure.string :as s]
   [datomic.client.api :as d]
   [datomic.schema :as ds]
   [graphql.fields :as fields]
   [graphql.types :as types]
   [shared.operations.operation :as o]
   [user :as u]))

(def prefix "get")

(defn query []
  (reify o/Operation
    (o/get-graphql-parent-type [_] types/query-type)
    (o/gen-graphql-field [_ entity]
      (fields/get-query entity))
    (o/gen-graphql-object-types [_ _])
    (o/resolves-graphql-field? [_ field-name]
      (s/starts-with? (name field-name) prefix))
    (o/resolve-field-data [_ conn {:keys [field-name arguments selected-paths]}]
      (let [gql-type  (s/replace (name field-name) prefix "")
            {:keys [id]} arguments
            entity-id (parse-long id)
            db        (d/db conn)
            schema    (ds/get-graphql-schema db)]
        (ds/pull-and-resolve-entity entity-id db gql-type selected-paths schema)))))

(comment
  (let [conn (u/temp-conn)]
    (time (o/resolve-field-data (query) conn {:field-name     :getPlanetaryBoundary
                                              :arguments      {:id "87960930222192"}
                                              :selected-paths #{"name"}})))
  (d/transact (u/temp-conn) {:tx-data [{:platform/name "Hello World!"}]})

  [(o/get-graphql-parent-type (query))
   (:name (o/gen-graphql-field (query) "Entity"))
   (o/gen-graphql-object-types (query) "Entity")
   (o/resolves-graphql-field? (query) "getPlanetaryBoundary")])
