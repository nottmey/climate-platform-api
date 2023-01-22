(ns shared.operations.delete
  (:require
    [clojure.string :as s]
    [graphql.arguments :as a]
    [graphql.types :as t]
    [shared.operations.operation :as o]))

(def prefix "delete")

(defn delete-mutation []
  ;; TODO implement resolver
  (reify o/Operation
    (get-graphql-parent-type [_] t/mutation-type)
    (gen-graphql-field [_ entity]
      {:name      (str prefix (name entity))
       :arguments [a/id]
       :type      entity})
    (gen-graphql-object-types [_ _])
    (resolves-graphql-field? [_ field]
      (s/starts-with? field prefix))))

(comment
  [(o/get-graphql-parent-type (delete-mutation))
   (:name (o/gen-graphql-field (delete-mutation) "Entity"))])