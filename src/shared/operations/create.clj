(ns shared.operations.create
  (:require
    [clojure.walk :as walk]
    [datomic.client.api :as d]
    [datomic.schema :as ds]
    [user :as u]
    [clojure.string :as s]
    [graphql.types :as t]
    [shared.operations.operation :as o]))

(def prefix "create")

(defn create-mutation []
  ;; TODO implement resolver
  (reify o/Operation
    (o/get-graphql-parent-type [_] t/mutation-type)
    (o/gen-graphql-field [_ entity]
      {:name           (str prefix (name entity))
       :arguments      [{:name           "value"
                         :type           (t/input-type entity)
                         :required-type? true}]
       :type           entity
       :required-type? true})
    (o/gen-graphql-object-types [_ _])
    (o/resolves-graphql-field? [_ field]
      (s/starts-with? field prefix))
    (o/resolve-field-data [_ conn {:keys [field-name arguments selected-paths]}]
      (let [gql-type   (s/replace field-name prefix "")
            gql-fields (set (filter #(not (s/includes? % "/")) selected-paths))
            {:keys [value]} arguments
            input      (walk/stringify-keys value)
            schema     (ds/get-graphql-schema (d/db conn))
            temp-id    "temp-id"
            input-data (-> (ds/resolve-input-fields input gql-type schema)
                           (assoc :db/id temp-id))
            {:keys [db-after tempids]} (d/transact conn {:tx-data [input-data]})
            entity-id  (get tempids temp-id)
            pattern    (ds/gen-pull-pattern gql-type gql-fields schema)
            entity     (->> [entity-id]
                            (ds/pull-entities db-after pattern)
                            (ds/reverse-pull-pattern gql-type gql-fields schema)
                            first)]
        entity))))

(comment
  (let [conn (u/sandbox-conn)]
    (time (o/resolve-field-data
            (create-mutation)
            conn
            {:field-name     "createPlanetaryBoundary"
             :arguments      {:value {:name "some planetary boundary"}}
             :selected-paths #{"name"}})))

  [(o/get-graphql-parent-type (create-mutation))
   (:name (o/gen-graphql-field (create-mutation) "Entity"))])