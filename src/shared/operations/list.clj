(ns shared.operations.list
  (:require [graphql.fields :as f]
            [graphql.types :as t]
            [shared.operations.operation :as o]))

(def list-query
  (reify o/Operation
    (get-graphql-parent-type [_] t/query-type)
    (gen-graphql-field [_ entity] (f/list-page-query entity))))

(comment
  [(o/get-graphql-parent-type list-query) (:name (o/gen-graphql-field list-query "Entity"))])