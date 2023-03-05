(ns graphql.objects
  (:require
   [graphql.fields :as fields]
   [graphql.types :as types]))

(defn list-page [entity]
  {:name   (types/list-page-type entity)
   :fields [fields/context
            {:name           :info
             :type           types/page-info-type
             :required-type? true}
            {:name           :values
             :type           entity
             :list?          true
             :required-type? true
             :required-list? true}]})