(ns shared.operations.create
  (:require
   [clojure.set :as set]
   [clojure.string :as s]
   [clojure.walk :as walk]
   [datomic.client.api :as d]
   [datomic.schema :as schema]
   [graphql.arguments :as arguments]
   [graphql.types :as types]
   [shared.operations.operation :as o]
   [shared.operations.publish-created :as publish-created]
   [user :as u]))

(def prefix "create")

(defn mutation []
  (reify o/Operation
    (o/get-graphql-parent-type [_] types/mutation-type)
    (o/gen-graphql-field [_ entity _]
      {:name           (str prefix (name entity))
       :arguments      [arguments/optional-session
                        {:name           "value"
                         :type           (types/input-type entity)
                         :required-type? true}]
       :type           entity
       :required-type? true})
    (o/gen-graphql-object-types [_ _])
    (o/resolves-graphql-field? [_ field-name]
      (s/starts-with? (name field-name) prefix))
    (o/get-resolver-location [_] :datomic)
    (o/resolve-field-data [_ {:keys [conn publish schema field-name arguments selected-paths]}]
      (let [gql-type      (s/replace (name field-name) prefix "")
            {:keys [session value]} arguments
            input         (walk/stringify-keys value)
            temp-id       "temp-id"
            input-data    (-> (schema/resolve-input-fields schema input gql-type)
                              (assoc :db/id temp-id))
            {:keys [db-after tempids]} (d/transact conn {:tx-data [input-data]})
            entity-id     (get tempids temp-id)
            default-paths (schema/get-default-paths schema gql-type)
            paths         (set/union selected-paths default-paths)
            entity        (-> (schema/pull-and-resolve-entity schema entity-id db-after gql-type paths)
                              (assoc "session" session))]
        ; TODO refactor publish to be a part of the return value
        (publish (o/create-publish-definition (publish-created/mutation)
                                              gql-type
                                              entity
                                              default-paths))
        entity))))

(comment
  (let [conn (u/temp-conn)]
    (o/resolve-field-data
     (mutation)
     {:conn           conn
      :initial-db     (d/db conn)
      :schema         (schema/get-schema (d/db conn))
      :publish        #(printf (str % "\n"))
      :field-name     :createPlanetaryBoundary
      :arguments      {:session "session id"
                       :value   {:name     "some planetary boundary"
                                 "session" "123"}}
      :selected-paths #{"name"}})))
