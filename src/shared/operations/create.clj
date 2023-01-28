(ns shared.operations.create
  (:require
    [clojure.string :as s]
    [graphql.types :as t]
    [shared.operations.operation :as o]))

(def prefix "create")

(defn create-mutation []
  ;; TODO implement resolver
  (reify o/Operation
    (o/get-graphql-parent-type [_] t/mutation-type)
    (o/gen-graphql-field [_ entity]
      {:name           (str prefix (name entity))
       :arguments      [{:name           "value"
                         :type           (t/input-type entity)
                         :required-type? true}]
       :type           entity
       :required-type? true})
    (o/gen-graphql-object-types [_ _])
    (o/resolves-graphql-field? [_ field]
      (s/starts-with? field prefix))))

(comment
  [(o/get-graphql-parent-type (create-mutation))
   (:name (o/gen-graphql-field (create-mutation) "Entity"))])