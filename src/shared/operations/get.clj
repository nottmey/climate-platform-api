(ns shared.operations.get
  (:require
   [clojure.string :as s]
   [datomic.client.api :as d]
   [datomic.schema :as schema]
   [graphql.fields :as fields]
   [graphql.types :as types]
   [shared.operations.operation :as o]
   [user :as u]))

(def prefix "get")

(defn query []
  (reify o/Operation
    (o/get-graphql-parent-type [_] types/query-type)
    (o/gen-graphql-field [_ entity _]
      (fields/get-query entity))
    (o/gen-graphql-object-types [_ _])
    (o/resolves-graphql-field? [_ field-name]
      (s/starts-with? (name field-name) prefix))
    (o/get-resolver-location [_] :datomic)
    (o/resolve-field-data [_ {:keys [initial-db schema field-name arguments selected-paths]}]
      (let [gql-type  (s/replace (name field-name) prefix "")
            {:keys [id]} arguments
            entity-id (parse-long id)]
        (schema/pull-and-resolve-entity schema entity-id initial-db gql-type selected-paths)))))

(comment
  (let [conn (u/temp-conn)]
    (time (o/resolve-field-data
           (query)
           {:conn           conn
            :initial-db     (d/db conn)
            :schema         (schema/get-schema (d/db conn))
            :field-name     :getPlanetaryBoundary
            :arguments      {:id "87960930222192"}
            :selected-paths #{"name"}}))))
