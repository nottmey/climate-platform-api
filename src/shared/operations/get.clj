(ns shared.operations.get
  (:require [graphql.fields :as f]
            [graphql.types :as t]
            [shared.operations.operation :as o]))

(def get-query
  (reify o/Operation
    (get-graphql-parent-type [_] t/query-type)
    (gen-graphql-field [_ entity] (f/get-query entity))
    (gen-graphql-object-types [_ _])))

(comment
  [(o/get-graphql-parent-type get-query)
   (:name (o/gen-graphql-field get-query "Entity"))
   (o/gen-graphql-object-types get-query "Entity")])
