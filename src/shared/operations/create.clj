(ns shared.operations.create
  (:require [graphql.types :as t]
            [shared.operations.operation :as o]))

(def create-mutation
  (reify o/Operation
    (get-graphql-parent-type [_] t/mutation-type)
    (gen-graphql-field [_ entity]
      {:name           (str "create" entity)
       :arguments      [{:name           "value"
                         :type           (t/input-type entity)
                         :required-type? true}]
       :type           entity
       :required-type? true})
    (gen-graphql-object-types [_ _])))

(comment
  [(o/get-graphql-parent-type create-mutation) (:name (o/gen-graphql-field create-mutation "Entity"))])