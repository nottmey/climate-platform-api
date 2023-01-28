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
    (o/resolves-graphql-field? [_ field]
      (s/starts-with? field prefix))
    (o/resolve-field-data [_ conn {:keys [field-name arguments selected-paths]}]
      (let [{:keys [id]} arguments
            entity-id  (parse-long id)
            gql-type   (s/replace field-name prefix "")
            gql-fields (set (filter #(not (s/includes? % "/")) selected-paths))
            db         (d/db conn)
            schema     (ds/get-graphql-schema db)
            pattern    (ds/gen-pull-pattern gql-type gql-fields schema)
            entity     (->> [entity-id]
                            (ds/pull-entities db pattern)
                            (ds/reverse-pull-pattern gql-type gql-fields schema)
                            first)]
        ; when entity has values, else it's not there (id is not checked, so pull has same input than output length)
        (when (> (count entity) 1)
          entity)))))

(comment
  (let [conn (u/sandbox-conn)]
    (time (o/resolve-field-data (get-query) conn {:field-name     "getPlanetaryBoundary"
                                                  :arguments      {:id "87960930222192"}
                                                  :selected-paths #{"name"}})))
  (d/transact (u/sandbox-conn) {:tx-data [{:platform/name "Hello World!"}]})

  [(o/get-graphql-parent-type (get-query))
   (:name (o/gen-graphql-field (get-query) "Entity"))
   (o/gen-graphql-object-types (get-query) "Entity")
   (o/resolves-graphql-field? (get-query) "getEntity")])
