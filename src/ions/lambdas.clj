(ns ions.lambdas
  (:require [clojure.data.json :as json]
            [clojure.walk :as walk]
            [ions.resolvers :as resolvers]))

; result needs to be string serialized json
; default response mapping is applied: https://docs.aws.amazon.com/appsync/latest/devguide/resolver-mapping-template-reference-lambda.html#lambda-mapping-template-bypass-response
; FYI if you want to add a batch resolver, you need to override the default request/response mapping
(defn datomic-resolver [{lambda-context :context
                         app-sync-input :input}]
  ; the so called `$context` in https://docs.aws.amazon.com/appsync/latest/devguide/resolver-context-reference.html
  (let [app-sync-context (json/read-str app-sync-input)
        parent-type-name (keyword (get-in app-sync-context ["info" "parentTypeName"]))
        field-name       (keyword (get-in app-sync-context ["info" "fieldName"]))
        ; TODO make resolvers only calculate requested values
        selected-paths   (set (get-in app-sync-context ["info" "selectionSetList"]))
        arguments        (walk/keywordize-keys (get app-sync-context "arguments"))
        parent-value     (walk/keywordize-keys (get app-sync-context "source"))
        ; TODO only append context in dev mode (e.g. api key or dev identity)
        assoc-context    (fn [m]
                           (if (and (map? m) (contains? selected-paths "context"))
                             (assoc m :context app-sync-context) m))]
    (-> {:parent-type-name parent-type-name
         :field-name       field-name
         :selected-paths   selected-paths
         :arguments        arguments
         :parent-value     parent-value}
        resolvers/datomic-resolve
        assoc-context
        json/write-str)))

(comment
  ; example
  (datomic-resolver {:context {:clientContext         nil,
                               :identity              nil,
                               :functionVersion       "$LATEST",
                               :memoryLimitInMB       256,
                               :invokedFunctionArn    "arn:aws:lambda:eu-central-1:118776085668:function:climate-platform-primary-hello-world",
                               :logGroupName          "/aws/lambda/climate-platform-primary-hello-world",
                               :logStreamName         "2022/12/10/[$LATEST]7451dd4cbdb24bcca811434033e3709c",
                               :awsRequestId          "f1d9c589-6335-4d00-9963-b7576a8704fc",
                               :functionName          "climate-platform-primary-hello-world",
                               :remainingTimeInMillis 59997}
                     :input   (json/write-str {"arguments" {"database" "datomic-docs-tutorial"},
                                               "identity"  nil,
                                               "source"    {"total" 60,
                                                            "slice" {"database" "datomic-docs-tutorial"}},
                                               "prev"      nil,
                                               "info"      {"selectionSetList"    ["context" "entities" "entities/id"], ; not consistent
                                                            "selectionSetGraphQL" "{\n  context\n  entities {\n    id\n  }\n}", ; not consistent
                                                            "fieldName"           "slice",
                                                            "parentTypeName"      "EntityList",
                                                            "variables"           {}},
                                               "stash"     {},
                                               "request"   {"headers" {"origin" "https://eu-central-1.console.aws.amazon.com",,,}}})}))
