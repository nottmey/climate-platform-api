(ns graphql.arguments
  (:require [graphql.types :as t]))

(def required-id
  {:name           :id
   :type           t/id-type
   :required-type? true})

(def optional-id
  {:name :id
   :type t/id-type})
