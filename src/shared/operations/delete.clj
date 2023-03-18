(ns shared.operations.delete
  (:require
   [clojure.set :as set]
   [clojure.string :as s]
   [datomic.client.api :as d]
   [datomic.schema :as schema]
   [graphql.arguments :as arguments]
   [graphql.types :as types]
   [shared.operations.operation :as o]
   [shared.operations.publish-deleted :as publish-deleted]
   [user :as u]))

(def prefix "delete")

(defn mutation []
  (reify o/Operation
    (o/get-graphql-parent-type [_] types/mutation-type)
    (o/gen-graphql-field [_ entity _]
      {:name      (str prefix (name entity))
       :arguments [arguments/required-id
                   arguments/optional-session]
       :type      entity})
    (o/gen-graphql-object-types [_ _])
    (o/resolves-graphql-field? [_ field-name]
      (s/starts-with? (name field-name) prefix))
    (o/get-resolver-location [_] :datomic)
    (o/resolve-field-data [_ {:keys [conn publish initial-db schema field-name arguments selected-paths]}]
      (let [gql-type      (s/replace (name field-name) prefix "")
            {:keys [id session]} arguments
            entity-id     (parse-long id)
            default-paths (schema/get-default-paths schema gql-type)
            paths         (set/union selected-paths default-paths)
            entity        (schema/pull-and-resolve-entity schema entity-id initial-db gql-type paths)]
        (if (nil? entity)
          nil
          (let [e-with-session (assoc entity "session" session)]
            (d/transact conn {:tx-data [[:db/retractEntity entity-id]]})
            (publish (o/create-publish-definition (publish-deleted/mutation)
                                                  gql-type
                                                  e-with-session
                                                  default-paths))
            e-with-session))))))

(comment
  (let [conn (u/temp-conn)
        {:keys [db-after tempids]} (d/transact conn {:tx-data [{:db/id          "tempid"
                                                                u/rel-attribute u/rel-sample-value}]})]
    (o/resolve-field-data
     (mutation)
     {:conn           conn
      :initial-db     db-after
      :schema         (schema/get-schema db-after)
      :publish        #(printf (str % "\n"))
      :field-name     :deletePlanetaryBoundary
      :arguments      {:id      (str (get tempids "tempid"))
                       :session "session id"}
      :selected-paths #{"name"}})))
