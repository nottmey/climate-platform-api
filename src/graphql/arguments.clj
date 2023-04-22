(ns graphql.arguments
  (:require [graphql.types :as types]))

(def required-id
  {:name           :id
   :type           types/id-type
   :required-type? true})

(def optional-id
  {:name :id
   :type types/id-type})

(defn required-input-value [entity-name]
  {:name           "value"
   :type           (types/input-type entity-name)
   :required-type? true})