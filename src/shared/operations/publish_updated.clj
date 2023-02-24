(ns shared.operations.publish-updated
  (:require
   [clojure.string :as s]
   [graphql.fields :as fields]
   [graphql.types :as types]
   [shared.operations.operation :as o]))

(def prefix "publishUpdated")

(defn mutation []
  (reify o/Operation
    (o/get-graphql-parent-type [_] types/mutation-type)
    (o/gen-graphql-field [_ entity _]
      (fields/publish-mutation prefix entity))
    (o/gen-graphql-object-types [_ _])
    (o/resolves-graphql-field? [_ field-name]
      (s/starts-with? (name field-name) prefix))
    (o/get-resolver-location [_])
    (o/resolve-field-data [_ _ _])))