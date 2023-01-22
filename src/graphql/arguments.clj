(ns graphql.arguments
  (:require [graphql.types :as t]))

(def id
  {:name           :id
   :type           t/id-type
   :required-type? true})