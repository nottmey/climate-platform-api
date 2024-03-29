(ns graphql.types)

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

; Standard Types
(def query-type :Query)
(def mutation-type :Mutation)
(def subscription-type :Subscription)

; Own Types
(def entity-type :Entity)
(def entity-base-type :EntityBase)
(def attribute-type :Attribute)
(def page-info-type :PageInfo)
(def page-query-type :PageQuery)

(defn list-page-type [entity]
  (keyword (str (name entity) "ListPage")))

(defn input-type [entity]
  (str (name entity) "Input"))