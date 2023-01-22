(ns shared.operations.get
  (:require
    [clojure.string :as s]
    [graphql.fields :as f]
    [graphql.types :as t]
    [shared.operations.operation :as o]))

(defn get-query []
  ;; TODO implement resolver
  (reify o/Operation
    (get-graphql-parent-type [_] t/query-type)
    (gen-graphql-field [_ entity]
      (f/get-query entity))
    (gen-graphql-object-types [_ _])
    (resolves-graphql-field? [_ field]
      (s/starts-with? field "get"))))

(comment
  [(o/get-graphql-parent-type (get-query))
   (:name (o/gen-graphql-field (get-query) "Entity"))
   (o/gen-graphql-object-types (get-query) "Entity")
   (o/resolves-graphql-field? (get-query) "getEntity")])
