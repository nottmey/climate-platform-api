(ns graphql.arguments
  (:require [graphql.types :as types]))

(def required-id
  {:name           :id
   :type           types/id-type
   :required-type? true})

(def optional-id
  {:name :id
   :type types/id-type})

(def optional-session
  {:name :sessionId
   :type types/id-type})