(ns shared.operations.replace
  (:require [graphql.arguments :as a]
            [graphql.types :as t]
            [shared.operations.operation :as o]))

(def replace-mutation
  (reify o/Operation
    (get-graphql-parent-type [_] t/mutation-type)
    (gen-graphql-field [_ entity]
      {:name      (str "replace" entity)
       :arguments [a/id
                   {:name           "value"
                    :type           (t/input-type entity)
                    :required-type? true}]
       :type      entity})))

(comment
  [(o/get-graphql-parent-type replace-mutation) (:name (o/gen-graphql-field replace-mutation "Entity"))])