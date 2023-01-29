(ns shared.operations.delete
  (:require
    [user :as u]
    [datomic.client.api :as d]
    [datomic.schema :as ds]
    [clojure.string :as s]
    [graphql.arguments :as a]
    [graphql.types :as t]
    [shared.operations.operation :as o]))

(def prefix "delete")

(defn delete-mutation []
  (reify o/Operation
    (o/get-graphql-parent-type [_] t/mutation-type)
    (o/gen-graphql-field [_ entity]
      {:name      (str prefix (name entity))
       :arguments [a/id]
       :type      entity})
    (o/gen-graphql-object-types [_ _])
    (o/resolves-graphql-field? [_ field]
      (s/starts-with? field prefix))
    (o/resolve-field-data [_ conn {:keys [field-name arguments selected-paths]}]
      (let [gql-type  (s/replace field-name prefix "")
            {:keys [id]} arguments
            entity-id (parse-long id)
            db        (d/db conn)
            schema    (ds/get-graphql-schema db)
            entity    (ds/pull-and-resolve-entity entity-id db gql-type selected-paths schema)
            _         (when entity
                        (d/transact conn {:tx-data [[:db/retractEntity entity-id]]}))]
        entity))))

(comment
  (let [conn (u/sandbox-conn)]
    (time (o/resolve-field-data
            (delete-mutation)
            conn
            {:field-name     "deletePlanetaryBoundary"
             :arguments      {:id "101155069755524"}
             :selected-paths #{"name"}})))

  [(o/get-graphql-parent-type (delete-mutation))
   (:name (o/gen-graphql-field (delete-mutation) "Entity"))])
