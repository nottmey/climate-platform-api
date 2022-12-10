(ns ions.lambdas
  (:require [clojure.pprint :as pp]
            [clojure.data.json :as json]))

(defn- write-edn-str ^String [x]
  (binding [*print-length* nil
            *print-level*  nil]
    (with-out-str (pp/pprint x))))

; result needs to be string serialized json
; default response mapping is applied: https://docs.aws.amazon.com/appsync/latest/devguide/resolver-mapping-template-reference-lambda.html#lambda-mapping-template-bypass-response
(defn datomic-resolver [{lambda-context :context
                         app-sync-input :input}]
  ; the so called `$context` in https://docs.aws.amazon.com/appsync/latest/devguide/resolver-context-reference.html
  (let [app-sync-context (json/read-str app-sync-input)]
    (json/write-str {"message" (write-edn-str app-sync-context)})))

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
                     :input   (json/write-str {"arguments" {},
                                               "identity"  nil,
                                               "source"    nil,
                                               "prev"      nil,
                                               "info"      {"selectionSetList"    ["message"],
                                                            "selectionSetGraphQL" "{\n  message\n}",
                                                            "fieldName"           "helloFromIon",
                                                            "parentTypeName"      "Query",
                                                            "variables"           {}},
                                               "stash"     {},
                                               "request"   {"headers" {"origin" "https://eu-central-1.console.aws.amazon.com",,,}}})}))
