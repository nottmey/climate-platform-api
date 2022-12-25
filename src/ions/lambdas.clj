(ns ions.lambdas
  (:require [clojure.data.json :as json]
            [clojure.walk :as walk]
            [ions.resolvers :as resolvers]
            [datomic.ion.cast :as cast]))

; result needs to be string serialized json
; default response mapping is applied: https://docs.aws.amazon.com/appsync/latest/devguide/resolver-mapping-template-reference-lambda.html#lambda-mapping-template-bypass-response
(defn datomic-resolver [{lambda-context :context
                         app-sync-input :input}]
  ; the so called `$context` in https://docs.aws.amazon.com/appsync/latest/devguide/resolver-context-reference.html
  (cast/event {:msg "ResolverContext" ::json app-sync-input})
  (let [app-sync-context (json/read-str app-sync-input)
        parent-type-name (keyword (get-in app-sync-context ["info" "parentTypeName"]))
        field-name       (keyword (get-in app-sync-context ["info" "fieldName"]))
        arguments        (walk/keywordize-keys (get app-sync-context "arguments"))]
    (-> {:parent-type-name parent-type-name
         :field-name       field-name
         :arguments        arguments}
        resolvers/datomic-resolve
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
                                               "source"    nil,
                                               "prev"      nil,
                                               "info"      {"selectionSetList"    ["message"], ; not consistent
                                                            "selectionSetGraphQL" "{\n  message\n}", ; not consistent
                                                            "fieldName"           "databases",
                                                            "parentTypeName"      "Query",
                                                            "variables"           {}},
                                               "stash"     {},
                                               "request"   {"headers" {"origin" "https://eu-central-1.console.aws.amazon.com",,,}}})}))
