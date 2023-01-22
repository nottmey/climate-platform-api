(ns shared.operations.list
  (:require
    [clojure.string :as s]
    [graphql.fields :as f]
    [graphql.objects :as obj]
    [graphql.types :as t]
    [shared.operations.operation :as o]))

(defn list-query []
  ;; TODO implement resolver
  (reify o/Operation
    (get-graphql-parent-type [_] t/query-type)
    (gen-graphql-field [_ entity]
      (f/list-page-query entity))
    (gen-graphql-object-types [_ entity]
      [(obj/list-page entity)])
    (resolves-graphql-field? [_ field]
      (s/starts-with? field "list"))))

(comment
  [(o/get-graphql-parent-type (list-query))
   (:name (o/gen-graphql-field (list-query) "Entity"))])