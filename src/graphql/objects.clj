(ns graphql.objects
  (:require
   [graphql.types :as types]))

(defn list-page [entity]
  {:name   (types/list-page-type entity)
   :fields [{:name           :info
             :type           types/page-info-type
             :required-type? true}
            {:name           :values
             :type           entity
             :list?          true
             :required-type? true
             :required-list? true}]})