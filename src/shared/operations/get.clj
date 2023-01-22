(ns shared.operations.get
  (:require
    [clojure.string :as s]
    [datomic.schema :as ds]
    [datomic.client.api :as d]
    [graphql.fields :as f]
    [graphql.types :as t]
    [shared.operations.operation :as o]
    [user :as u]))

(def prefix "get")

(defn get-query []
  (reify o/Operation
    (get-graphql-parent-type [_] t/query-type)
    (gen-graphql-field [_ entity]
      (f/get-query entity))
    (gen-graphql-object-types [_ _])
    (resolves-graphql-field? [_ field]
      (s/starts-with? field prefix))
    (resolve-field-data [_ {:keys [field-name arguments selected-paths]}]
      (let [{:keys [id]} arguments
            entity-id  (parse-long id)
            gql-type   (s/replace field-name prefix "")
            gql-fields (filter #(not (s/includes? % "/")) selected-paths)
            db         (d/db (u/sandbox-conn))
            triples    (->> gql-fields (map #(vector gql-type entity-id %)))]
        (get-in
          (ds/get-specific-values db triples)
          [gql-type entity-id])))))

(comment
  (time (o/resolve-field-data (get-query) {:field-name     "getPlanetaryBoundary"
                                           :arguments      {:id "87960930222192"}
                                           :selected-paths #{"name"}}))
  (d/transact (u/sandbox-conn) {:tx-data [{:platform/name "Hello World!"}]})

  [(o/get-graphql-parent-type (get-query))
   (:name (o/gen-graphql-field (get-query) "Entity"))
   (o/gen-graphql-object-types (get-query) "Entity")
   (o/resolves-graphql-field? (get-query) "getEntity")])
