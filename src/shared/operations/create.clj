(ns shared.operations.create
  (:require
   [clojure.string :as s]
   [clojure.walk :as walk]
   [datomic.client.api :as d]
   [datomic.schema :as schema]
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
    (o/resolve-field-data [_ {:keys [conn schema field-name arguments selected-paths]}]
      (let [gql-type   (s/replace (name field-name) prefix "")
            {:keys [value]} arguments
            input      (walk/stringify-keys value)
            temp-id    "temp-id"
            input-data (-> (schema/resolve-input-fields schema input gql-type)
                           (assoc :db/id temp-id))
            {:keys [db-after tempids]} (d/transact conn {:tx-data [input-data]})
            entity-id  (get tempids temp-id)
            entity     (schema/pull-and-resolve-entity schema entity-id db-after gql-type selected-paths)]
        #_(let [publish-op    (publish-created/mutation)
                field-name    (:name (o/gen-graphql-field publish-op gql-type {}))
                mutation-name (str (s/upper-case (subs field-name 0 1))
                                   (subs field-name 1))]
            (publish (str
                      #_"mutation "
                      #_mutation-name
                      #_" { "
                      #_field-name
                      "(id: \""
                      entity-id
                      "\", value: {"
                      (->> (vals (get (:types schema) gql-type))
                           (map (fn [{:keys [graphql.relation/field]}]
                                  (str field ": " (get entity field))
                                  ; TODO also select all shallow paths (in selected-paths)
                                  ; TODO report the whole shallow object
                                  ))
                           (s/join ", ")))))
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
      :arguments      {:value {:name "some planetary boundary"}}
      :selected-paths #{"name"}})))

#_"mutation PublishCreatedPlanetaryBoundary {
      publishCreatedPlanetaryBoundary(id: \"123123123\", value: {name: \"Climate Change\"}) {
        id
        name
      }
    }"
