(ns ions.lambdas
  (:require
   [clj-http.client :as client]
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [clojure.walk :as walk]
   [datomic.access :as access]
   [ions.logging :as logging]
   [ions.resolvers :as resolvers]
   [testing :as t]
   [utils :as utils])
  (:import (clojure.lang ExceptionInfo)))

; example:
#_(datomic-resolver
   {:context {:clientContext         nil,
              :identity              nil,
              :functionVersion       "$LATEST",
              :memoryLimitInMB       256,
              :invokedFunctionArn    "arn:aws:lambda:eu-central-1:118776085668:function:climate-platform-primary-hello-world",
              :logGroupName          "/aws/lambda/climate-platform-primary-hello-world",
              :logStreamName         "2022/12/10/[$LATEST]7451dd4cbdb24bcca811434033e3709c",
              :awsRequestId          "f1d9c589-6335-4d00-9963-b7576a8704fc",
              :functionName          "climate-platform-primary-hello-world",
              :remainingTimeInMillis 59997}
    :input   (json/write-str
              {"arguments" {"database" "datomic-docs-tutorial"},
               "identity"  nil,
               "source"    {"total" 60,
                            "slice" {"database" "datomic-docs-tutorial"}},
               "prev"      nil,
               "info"      {"selectionSetList"    ["context" "entities" "entities/id"],
                            "selectionSetGraphQL" "{\n  context\n  entities {\n    id\n  }\n}",
                            "fieldName"           "slice",
                            "parentTypeName"      "EntityList",
                            "variables"           {}},
               "stash"     {},
               "request"   {"headers"
                            {"cloudfront-is-smarttv-viewer" "false",
                             "origin"                       "https//eu-central-1.console.aws.amazon.com",
                             "x-amzn-requestid"             "55d8991f-3afd-4cd3-a2fe-528df075b9c6",
                             "sec-fetch-site"               "cross-site",
                             "sec-ch-ua-mobile"             "?0",
                             "via"                          "2.0 ebfd02322356b60fe506d9cd1ca49956.cloudfront.net (CloudFront)",
                             "host"                         "3vbfo5wg6vbibbmesukjlqaw74.appsync-api.eu-central-1.amazonaws.com",
                             "user-agent"                   "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36",
                             "content-type"                 "application/json",
                             "content-length"               "109",
                             "cloudfront-is-desktop-viewer" "true",
                             "x-forwarded-port"             "443",
                             "cloudfront-viewer-asn"        "15943",
                             "referer"                      "https//eu-central-1.console.aws.amazon.com/",
                             "cloudfront-forwarded-proto"   "https",
                             "accept"                       "application/json, text/plain, */*",
                             "accept-language"              "en-GB,en;q=0.9,en-US;q=0.8,de;q=0.7",
                             "cloudfront-viewer-country"    "DE",
                             "cloudfront-is-tablet-viewer"  "false",
                             "sec-fetch-dest"               "empty",
                             "x-amzn-trace-id"              "Root=1-640392ac-5f19f9fe5b9ce12d2555b9d0",
                             "x-forwarded-for"              "192.196.202.250, 15.158.45.53",
                             "accept-encoding"              "gzip, deflate, br",
                             "x-forwarded-proto"            "https",
                             "cloudfront-is-mobile-viewer"  "false",
                             "x-amz-user-agent"             "AWS-Console-AppSync/",
                             "sec-fetch-mode"               "cors",
                             "x-api-key"                    "...",
                             "x-amz-cf-id"                  "wihEH19Jthx2JDGcJ6WaDNJScx4_Xwtd1UL9daZLlwKo3O5mmP2GYQ=="}}})})

(defn build-publish [headers]
  (let [url         (str "https://" (get headers "host") "/graphql")
        req-headers {"Content-Type" "application/graphql"
                     "x-api-key"    (get headers "x-api-key")}]
    ; TODO don't use the client api key (because publish needs to be restricted)
    (fn publish [query]
      (try
        (client/post url
                     {:headers req-headers
                      :body    (json/write-str {"query" query})})
        (catch ExceptionInfo e
          (Exception. (->> (-> (ex-data e)
                               :body
                               json/read-str
                               (get "errors"))
                           (map #(str (get % "errorType") ": " (get % "message")))
                           (str/join "\n"))))))))

(deftest build-publish-test
  (let [host  "some.host"
        key   "some key"
        query "mutation SomeQuery { getX(id: \"1\") { id } }"]
    (with-redefs [client/post (fn [url {:keys [headers body]}]
                                (is (= (str "https://" host "/graphql") url))
                                (is (= "application/graphql" (get headers "Content-Type")))
                                (is (= key (get headers "x-api-key")))
                                (let [b (json/read-str body)]
                                  (is (= query (get b "query")))))]
      ((build-publish
        {"host"      host
         "x-api-key" key})
       query))))

(comment
  ((build-publish {"host"      "3vbfo5wg6vbibbmesukjlqaw74.appsync-api.eu-central-1.amazonaws.com"
                   "x-api-key" "..."})
   ; query for all fields, only then they are pushed in subscriptions
   "mutation PublishUpdatedPlanetaryBoundary {
      publishUpdatedPlanetaryBoundary(id: \"b50ed0a0-dbe5-492b-b542-d2585bd1070c\", value: {name: \"Climate Change 3\", description: \"hello world!\\n# 123\"}) {
        id
        name
        description
      }
    }"))

; the result needs to be a string containing serialized json
; default response mapping is applied: https://docs.aws.amazon.com/appsync/latest/devguide/resolver-mapping-template-reference-lambda.html#lambda-mapping-template-bypass-response
; FYI if you want to add a batch resolver, you need to override the default request/response mapping
(defn datomic-resolver [{_lambda-context :context
                         lambda-input    :input}]
  ; the so-called `$context` in https://docs.aws.amazon.com/appsync/latest/devguide/resolver-context-reference.html
  (let [app-sync-context (json/read-str lambda-input)
        _                (logging/info "ResolvingField" {:app-sync-context app-sync-context})
        parent-type-name (keyword (get-in app-sync-context ["info" "parentTypeName"]))
        field-name       (keyword (get-in app-sync-context ["info" "fieldName"]))
        ; TODO adapt to use selectionSetGraphQL, so renamed fields are answered correctly
        ; TODO rename selected paths to show that it is a set
        selected-paths   (set (get-in app-sync-context ["info" "selectionSetList"]))
        ; TODO rework keywordizing inputs
        arguments        (walk/keywordize-keys (get app-sync-context "arguments"))
        parent-value     (walk/keywordize-keys (get app-sync-context "source"))
        conn             (if (utils/test-mode?)
                           ; making sure never to write to the real database in tests
                           (t/testing-conn)
                           (access/get-connection access/dev-env-db-name))
        publish          (if (utils/test-mode?)
                           ; making sure never to publish to the real system in tests
                           t/testing-publish
                           (build-publish (get-in app-sync-context ["request" "headers"])))
        resolve-result   (resolvers/select-and-use-resolver
                          {:conn             conn
                           :parent-type-name parent-type-name
                           :field-name       field-name
                           :selected-paths   selected-paths
                           :arguments        arguments
                           :parent-value     parent-value})
        {:keys [response publish-queries]} resolve-result]
    (when publish-queries
      (doseq [query publish-queries]
        (publish query)))
    (-> response
        json/write-str)))

(defn call-datomic-resolver
  [parent-type-name field-name selection-list arguments]
  (let [input-obj {"info"      {"parentTypeName"   parent-type-name
                                "fieldName"        field-name
                                "selectionSetList" selection-list}
                   "arguments" arguments}
        input-raw (json/write-str input-obj)]
    (json/read-str
     (datomic-resolver
      {:input input-raw}))))

(comment
  ; for doing real calls to the database via repl
  (call-datomic-resolver
   "Query"
   "getQuantification"
   ["id", "name", "dataPoints", "dataPoints/id", "planetaryBoundaries", "planetaryBoundaries/id"]
   {"id" "14ef1024-cbb8-408c-87d3-9c94fe73ea67"}))
