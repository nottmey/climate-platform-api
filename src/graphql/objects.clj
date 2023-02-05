(ns graphql.objects
  (:require
   [graphql.fields :as f]
   [graphql.types :as t]))

(defn list-page [entity]
  {:name   (t/list-page-type entity)
   :fields [f/context
            {:name           :info
             :type           t/page-info-type
             :required-type? true}
            {:name           :values
             :type           entity
             :list?          true
             :required-type? true
             :required-list? true}]})