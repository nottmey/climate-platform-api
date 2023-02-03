(ns shared.operations.get
  (:require
    [shared.operations.operation :as o]
    [clojure.string :as s]
    [datomic.schema :as ds]
    [datomic.client.api :as d]
    [graphql.fields :as f]
    [graphql.types :as t]
    [user :as u]))

(def prefix "get")

(defn get-query []
  (reify o/Operation
    (o/get-graphql-parent-type [_] t/query-type)
    (o/gen-graphql-field [_ entity]
      (f/get-query entity))
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
  (let [conn (u/sandbox-conn)]
    (time (o/resolve-field-data (get-query) conn {:field-name     :getPlanetaryBoundary
                                                  :arguments      {:id "87960930222192"}
                                                  :selected-paths #{"name"}})))
  (d/transact (u/sandbox-conn) {:tx-data [{:platform/name "Hello World!"}]})

  [(o/get-graphql-parent-type (get-query))
   (:name (o/gen-graphql-field (get-query) "Entity"))
   (o/gen-graphql-object-types (get-query) "Entity")
   (o/resolves-graphql-field? (get-query) "getPlanetaryBoundary")])
