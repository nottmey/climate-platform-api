(ns shared.operations.on-updated
  (:require
   [clojure.string :as s]
   [graphql.fields :as fields]
   [graphql.types :as types]
   [shared.operations.operation :as o]
   [shared.operations.publish-updated :as publish-updated]))

(def prefix "onUpdated")

(defn subscription []
  (reify o/Operation
    (o/get-graphql-parent-type [_] types/subscription-type)
    (o/gen-graphql-field [_ entity fields]
      (fields/subscription prefix entity fields (publish-updated/mutation)))
    (o/gen-graphql-object-types [_ _])
    (o/resolves-graphql-field? [_ field-name]
      (s/starts-with? (name field-name) prefix))
    (o/get-resolver-location [_])
    (o/resolve-field-data [_ _ _])))
