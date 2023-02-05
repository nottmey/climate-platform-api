(ns graphql.fields
  (:require [graphql.arguments :as a]
            [graphql.types :as t]))

(def context
  {:name :context
   :type t/json-type})

(defn get-query [entity]
  {:name      (keyword (str "get" (name entity)))
   :arguments [a/id]
   :type      (name entity)})

(defn list-page-query
  ([entity] (list-page-query entity nil))
  ([entity filter-type]
   {:name           (keyword (str "list" (name entity)))
    :arguments      (concat
                     [{:name :page
                       :type t/page-query-type}]
                     (when filter-type
                       [{:name :filter
                         :type filter-type}]))
    :type           (t/list-page-type entity)
    :required-type? true}))