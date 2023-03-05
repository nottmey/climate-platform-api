(ns shared.operations.delete
  (:require
   [clojure.string :as s]
   [datomic.client.api :as d]
   [datomic.schema :as schema]
   [graphql.arguments :as arguments]
   [graphql.types :as types]
   [shared.operations.operation :as o]
   [user :as u]))

(def prefix "delete")

(defn mutation []
  (reify o/Operation
    (o/get-graphql-parent-type [_] types/mutation-type)
    (o/gen-graphql-field [_ entity _]
      {:name      (str prefix (name entity))
       :arguments [arguments/required-id]
       :type      entity})
    (o/gen-graphql-object-types [_ _])
    (o/resolves-graphql-field? [_ field-name]
      (s/starts-with? (name field-name) prefix))
    (o/get-resolver-location [_] :datomic)
    (o/resolve-field-data [_ {:keys [conn initial-db schema field-name arguments selected-paths]}]
      (let [gql-type  (s/replace (name field-name) prefix "")
            {:keys [id]} arguments
            entity-id (parse-long id)
            entity    (schema/pull-and-resolve-entity schema entity-id initial-db gql-type selected-paths)
            _         (when entity
                        (d/transact conn {:tx-data [[:db/retractEntity entity-id]]}))]
        entity))))

(comment
  (let [conn (u/temp-conn)]
    (time (o/resolve-field-data
           (mutation)
           {:conn           conn
            :initial-db     (d/db conn)
            :schema         (schema/get-schema (d/db conn))
            :field-name     :deletePlanetaryBoundary
            :arguments      {:id "101155069755524"}
            :selected-paths #{"name"}}))))
