(ns shared.operations.replace
  (:require
   [clojure.string :as s]
   [graphql.arguments :as arguments]
   [graphql.types :as types]
   [shared.operations.operation :as o]))

(def prefix "replace")

(defn mutation []
  ;; TODO implement resolver
  (reify o/Operation
    (o/get-graphql-parent-type [_] types/mutation-type)
    (o/gen-graphql-field [_ entity]
      {:name      (str prefix (name entity))
       :arguments [arguments/id
                   {:name           "value"
                    :type           (types/input-type entity)
                    :required-type? true}]
       :type      entity})
    (o/gen-graphql-object-types [_ _])
    (o/resolves-graphql-field? [_ field-name]
      (s/starts-with? (name field-name) prefix))))

(comment
  [(o/get-graphql-parent-type (mutation))
   (:name (o/gen-graphql-field (mutation) "Entity"))])