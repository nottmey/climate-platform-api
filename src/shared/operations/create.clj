(ns shared.operations.create
  (:require
   [clojure.string :as s]
   [clojure.walk :as walk]
   [datomic.client.api :as d]
   [datomic.schema :as ds]
   [graphql.types :as types]
   [shared.operations.operation :as o]
   [user :as u]))

(def prefix "create")

(defn mutation []
  (reify o/Operation
    (o/get-graphql-parent-type [_] types/mutation-type)
    (o/gen-graphql-field [_ entity _]
      {:name           (str prefix (name entity))
       :arguments      [{:name           "value"
                         :type           (types/input-type entity)
                         :required-type? true}]
       :type           entity
       :required-type? true})
    (o/gen-graphql-object-types [_ _])
    (o/resolves-graphql-field? [_ field-name]
      (s/starts-with? (name field-name) prefix))
    (o/get-resolver-location [_] :datomic)
    (o/resolve-field-data [_ conn {:keys [field-name arguments selected-paths]}]
      (let [gql-type   (s/replace (name field-name) prefix "")
            {:keys [value]} arguments
            input      (walk/stringify-keys value)
            schema     (ds/get-graphql-schema (d/db conn))
            temp-id    "temp-id"
            input-data (-> (ds/resolve-input-fields input gql-type schema)
                           (assoc :db/id temp-id))
            {:keys [db-after tempids]} (d/transact conn {:tx-data [input-data]})
            entity-id  (get tempids temp-id)]
        (ds/pull-and-resolve-entity entity-id db-after gql-type selected-paths schema)))))

(comment
  (let [conn (u/temp-conn)]
    (time (o/resolve-field-data
           (mutation)
           conn
           {:field-name     :createPlanetaryBoundary
            :arguments      {:value {:name "some planetary boundary"}}
            :selected-paths #{"name"}}))))
