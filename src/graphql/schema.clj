(ns graphql.schema
  (:require [clojure.java.io :as io]
            [graphql.definitions :as d]))

; GraphQL Default Scalars
(def id-type :ID)
(def string-type :String)
(def int-type :Int)
(def float-type :Float)
(def boolean-type :Boolean)

; AWS App Sync also has scalars build in, but does not support custom ones:
; https://docs.aws.amazon.com/appsync/latest/devguide/scalars.html

; AWSEmail      "example@example.com"
(def email-type :AWSEmail)

; AWSJSON       "{\"a\":1, \"b\":3, \"string\": 234}"
(def json-type :AWSJSON)

; AWSDate       "1970-01-01Z"
(def date-type :AWSDate)

; AWSTime       "12:00:34."
(def time-type :AWSTime)

; AWSDateTime   "1930-01-01T16:00:00-07:00"
(def date-time-type :AWSDateTime)

; AWSTimestamp  -123123
(def timestamp-type :AWSTimestamp)

; AWSURL        "https://amazon.com"
(def url-type :AWSURL)

; AWSPhone      "+1 555 764 4377"
(def phone-type :AWSPhone)

; AWSIPAddress  "127.0.0.1/8"
(def ip-address-type :AWSIPAddress)

(defn generate []
  (let [query-type             :Query
        mutation-type          :Mutation
        entity-type            :Entity
        attribute-type         :Attribute
        entity-list-type       (keyword (str (name entity-type) "List"))
        entity-list-slice-type (keyword (str (name entity-list-type) "Slice"))
        database-argument      {:name           :database
                                :type           id-type
                                :required-type? true}
        id-argument            {:name           :id
                                :type           id-type
                                :required-type? true}
        limit-argument         {:name          :limit
                                ; FYI discarded by App Sync
                                :default-value 20
                                :type          int-type}
        offset-argument        {:name          :offset
                                ; FYI discarded by App Sync
                                :default-value 0
                                :type          int-type}
        context-field          {:name :context
                                :type json-type}]
    (str
      "# generated, do not edit manually!\n\n"
      (d/schema-definition
        {:root-ops {:query    query-type
                    :mutation mutation-type}})
      ; TODO generate from resolvers (via annotations on resolvers)
      (d/object-type-definition
        {:name   query-type
         :fields [{:name           :databases
                   :type           id-type
                   :list?          true
                   :required-type? true
                   :required-list? true}
                  {:name      :get
                   :arguments [database-argument id-argument]
                   :type      entity-type}
                  {:name           :list
                   :arguments      [database-argument]
                   :type           entity-list-type
                   :required-type? true}]})
      "# example mutation, so it's not empty\n"
      (d/object-type-definition
        {:name   mutation-type
         :fields [{:name           :reset
                   :type           entity-type
                   :list?          true
                   :required-type? true
                   :required-list? true}]})
      (d/object-type-definition
        {:name   entity-type
         :fields [context-field
                  {:name           :id
                   :type           id-type
                   :required-type? true}
                  {:name           :attributes
                   :type           attribute-type
                   :list?          true
                   :required-type? true
                   :required-list? true}]})
      (d/object-type-definition
        {:name   attribute-type
         :fields [{:name           :id
                   :type           id-type
                   :required-type? true}
                  {:name           :name
                   :type           string-type
                   :required-type? true}
                  {:name           :type
                   :type           string-type
                   :required-type? true}
                  {:name           :values
                   ; TODO use attribute interface type with different value types
                   :type           string-type
                   :list?          true
                   :required-type? true
                   :required-list? true}]})
      (d/object-type-definition
        {:name   entity-list-type
         :fields [context-field
                  {:name           :total
                   :type           int-type
                   :required-type? true}
                  {:name           :slice
                   :arguments      [limit-argument offset-argument]
                   :type           entity-list-slice-type
                   :required-type? true}]})
      (d/object-type-definition
        {:name   entity-list-slice-type
         :fields [context-field
                  {:name           "usedLimit"
                   :type           int-type
                   :required-type? true}
                  {:name           "usedOffset"
                   :type           int-type
                   :required-type? true}
                  {:name           :entities
                   :type           entity-type
                   :list?          true
                   :required-type? true
                   :required-list? true}]}))))

(comment
  (printf (generate))
  (spit (io/resource "cdk/schema.graphql") (generate)))