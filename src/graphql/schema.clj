(ns graphql.schema
  (:require [graphql.definitions :as d]
            [clojure.java.io :as io]))

; AWS App Sync also has scalars build in, but does not support custom ones:
; https://docs.aws.amazon.com/appsync/latest/devguide/scalars.html
; AWSEmail      "example@example.com"
; AWSJSON       "{\"a\":1, \"b\":3, \"string\": 234}"
; AWSDate       "1970-01-01Z"
; AWSTime       "12:00:34."
; AWSDateTime   "1930-01-01T16:00:00-07:00"
; AWSTimestamp  -123123
; AWSURL        "https://amazon.com"
; AWSPhone      "+1 555 764 4377"
; AWSIPAddress  "127.0.0.1/8"
(defn generate []
  (let [query-type        :Query
        mutation-type     :Mutation
        entity-type       :Entity
        attribute-type    :Attribute
        database-argument {:name :database :type :ID :required-type? true}
        id-argument       {:name :id :type :ID :required-type? true}
        limit-argument    {:name :limit :type :Int}
        offset-argument   {:name :offset :type :Int}]
    (str
      (d/schema-definition
        {:root-ops {:query    query-type
                    :mutation mutation-type}})
      ; TODO generate from resolvers (via annotations on resolvers)
      (d/object-type-definition
        {:name   query-type
         :fields [{:name           :databases
                   :type           :ID
                   :list?          true
                   :required-type? true}
                  {:name      :get
                   :arguments [database-argument id-argument]
                   :type      entity-type}
                  {:name           :list
                   :arguments      [database-argument limit-argument offset-argument]
                   :type           entity-type
                   :list?          true
                   :required-type? true}]})
      "# example mutation, so it's not empty\n"
      (d/object-type-definition
        {:name   mutation-type
         :fields [{:name           :reset
                   :type           entity-type
                   :list?          true
                   :required-type? true}]})
      (d/object-type-definition
        {:name   entity-type
         :fields [{:name           :id
                   :type           :ID
                   :required-type? true}
                  {:name           :attributes
                   :type           attribute-type
                   :list?          true
                   :required-type? true}]})
      (d/object-type-definition
        {:name   attribute-type
         :fields [{:name           :id
                   :type           :ID
                   :required-type? true}
                  {:name           :name
                   :type           :String
                   :required-type? true}
                  {:name           :type
                   :type           :String
                   :required-type? true}
                  {:name           :values
                   ; TODO use attribute interface type with different value types
                   :type           :String
                   :required-type? true
                   :list?          true}]}))))

(comment
  (printf (generate))
  (spit (io/resource "cdk/schema.graphql") (generate)))