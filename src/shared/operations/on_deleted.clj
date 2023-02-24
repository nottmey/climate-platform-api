(ns shared.operations.on-deleted
  (:require
   [clojure.string :as s]
   [graphql.fields :as fields]
   [graphql.types :as types]
   [shared.operations.operation :as o]
   [shared.operations.publish-deleted :as publish-deleted]))

(def prefix "onDeleted")

(defn subscription []
  (reify o/Operation
    (o/get-graphql-parent-type [_] types/subscription-type)
    (o/gen-graphql-field [_ entity fields]
      (fields/subscription prefix entity fields (publish-deleted/mutation)))
    (o/gen-graphql-object-types [_ _])
    (o/resolves-graphql-field? [_ field-name]
      (s/starts-with? (name field-name) prefix))
    (o/get-resolver-location [_])
    (o/resolve-field-data [_ _ _])))
