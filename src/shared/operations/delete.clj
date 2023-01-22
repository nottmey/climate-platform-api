(ns shared.operations.delete
  (:require [graphql.arguments :as a]
            [graphql.types :as t]
            [shared.operations.operation :as o]))

(def delete-mutation
  (reify o/Operation
    (get-graphql-parent-type [_] t/mutation-type)
    (gen-graphql-field [_ entity]
      {:name      (str "delete" entity)
       :arguments [a/id]
       :type      entity})))

(comment
  [(o/get-graphql-parent-type delete-mutation) (:name (o/gen-graphql-field delete-mutation "Entity"))])