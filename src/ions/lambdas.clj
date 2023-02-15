(ns ions.lambdas
  (:require
   [clojure.data.json :as json]
   [clojure.test :refer [deftest is]]
   [clojure.walk :as walk]
   [datomic.access :as da]
   [ions.resolvers :as resolvers]
   [user :as u]))

; example:
;  (datomic-resolver
;    {:context {:clientContext         nil,
;               :identity              nil,
;               :functionVersion       "$LATEST",
;               :memoryLimitInMB       256,
;               :invokedFunctionArn    "arn:aws:lambda:eu-central-1:118776085668:function:climate-platform-primary-hello-world",
;               :logGroupName          "/aws/lambda/climate-platform-primary-hello-world",
;               :logStreamName         "2022/12/10/[$LATEST]7451dd4cbdb24bcca811434033e3709c",
;               :awsRequestId          "f1d9c589-6335-4d00-9963-b7576a8704fc",
;               :functionName          "climate-platform-primary-hello-world",
;               :remainingTimeInMillis 59997}
;     :input   (json/write-str
;                {"arguments" {"database" "datomic-docs-tutorial"},
;                 "identity"  nil,
;                 "source"    {"total" 60,
;                              "slice" {"database" "datomic-docs-tutorial"}},
;                 "prev"      nil,
;                 "info"      {"selectionSetList"    ["context" "entities" "entities/id"],
;                              "selectionSetGraphQL" "{\n  context\n  entities {\n    id\n  }\n}",
;                              "fieldName"           "slice",
;                              "parentTypeName"      "EntityList",
;                              "variables"           {}},
;                 "stash"     {},
;                 "request"   {"headers" {"origin" "https://eu-central-1.console.aws.amazon.com",,,}}})})

; result needs to be string serialized json
; default response mapping is applied: https://docs.aws.amazon.com/appsync/latest/devguide/resolver-mapping-template-reference-lambda.html#lambda-mapping-template-bypass-response
; FYI if you want to add a batch resolver, you need to override the default request/response mapping
(defn datomic-resolver [{_lambda-context :context
                         app-sync-input  :input
                         ; used exclusively in test-mode; never using the real database in tests
                         testing-conn    :testing-conn}]
  ; the so called `$context` in https://docs.aws.amazon.com/appsync/latest/devguide/resolver-context-reference.html
  (let [app-sync-context (json/read-str app-sync-input)
        parent-type-name (keyword (get-in app-sync-context ["info" "parentTypeName"]))
        field-name       (keyword (get-in app-sync-context ["info" "fieldName"]))
        selection-set    (set (get-in app-sync-context ["info" "selectionSetList"]))
        ; TODO only append context in dev mode (e.g. api key or dev identity)
        assoc-context    (fn [m]
                           (if (and (map? m) (contains? selection-set "context"))
                             (assoc m :context app-sync-context)
                             m))
        selected-paths   (disj selection-set "context")
        arguments        (walk/keywordize-keys (get app-sync-context "arguments"))
        parent-value     (walk/keywordize-keys (get app-sync-context "source"))
        conn             (if (u/test-mode?)
                           (or testing-conn (throw (AssertionError. "missing test database")))
                           (da/get-connection da/dev-env-db-name))]
    (-> {:parent-type-name parent-type-name
         :field-name       field-name
         :selected-paths   selected-paths
         :arguments        arguments
         :parent-value     parent-value}
        (resolvers/select-and-use-correct-resolver conn)
        assoc-context
        json/write-str)))

(deftest test-get-non-existent-id
  (let [conn     (u/temp-conn)
        response (json/read-str
                  (datomic-resolver
                   {:testing-conn conn
                    :input        (json/write-str
                                   {"info"      {"parentTypeName" "Query"
                                                 "fieldName"      (str "get" u/rel-type)}
                                    "arguments" {"id" "123"}})}))]
    (is (= response nil))))

(deftest test-list-empty-db
  (let [conn     (u/temp-conn)
        response (json/read-str
                  (datomic-resolver
                   {:testing-conn conn
                    :input        (json/write-str
                                   {"info"      {"parentTypeName" "Query"
                                                 "fieldName"      (str "list" u/rel-type)}
                                    "arguments" {}})}))
        expected {"info"   {"size"    20
                            "offset"  0
                            "first"   0
                            "prev"    nil
                            "current" 0
                            "next"    nil
                            "last"    0}
                  "values" []}]
    (is (= expected response))))

(deftest test-create-with-get-and-list
  (let [conn           (u/temp-conn)
        created-entity (json/read-str
                        (datomic-resolver
                         {:testing-conn conn
                          :input        (json/write-str
                                         {"info"      {"parentTypeName"   "Mutation"
                                                       "fieldName"        (str "create" u/rel-type)
                                                       "selectionSetList" ["id" u/rel-field]}
                                          "arguments" {"value" {u/rel-field u/rel-sample-value}}})}))
        entity-id      (get created-entity "id")]
    (is (= u/rel-sample-value (get created-entity u/rel-field)))
    (is (string? entity-id))

    (let [fetched-entity
          (json/read-str
           (datomic-resolver
            {:testing-conn conn
             :input        (json/write-str
                            {"info"      {"parentTypeName"   "Query"
                                          "fieldName"        (str "get" u/rel-type)
                                          "selectionSetList" ["id" u/rel-field]}
                             "arguments" {"id" entity-id}})}))]
      (is (= created-entity fetched-entity)))

    (let [entity-list
          (json/read-str
           (datomic-resolver
            {:testing-conn conn
             :input        (json/write-str
                            {"info"      {"parentTypeName"   "Query"
                                          "fieldName"        (str "list" u/rel-type)
                                          "selectionSetList" ["values/id" (str "values/" u/rel-field)]}
                             "arguments" {"page" {"size" 10}}})}))
          expected-list
          {"info"   {"size"    10
                     "offset"  0
                     "first"   0
                     "prev"    nil
                     "current" 0
                     "next"    nil
                     "last"    0}
           "values" [created-entity]}]
      (is (= expected-list entity-list)))

    (let [deleted-entity
          (json/read-str
           (datomic-resolver
            {:testing-conn conn
             :input        (json/write-str
                            {"info"      {"parentTypeName"   "Mutation"
                                          "fieldName"        (str "delete" u/rel-type)
                                          "selectionSetList" ["id" u/rel-field]}
                             "arguments" {"id" entity-id}})}))]
      (is (= created-entity deleted-entity)))))

(deftest test-entity-browser-get
  (let [conn     (u/temp-conn)
        response (json/read-str
                  (datomic-resolver
                   {:testing-conn conn
                    :input        (json/write-str
                                   {"info"      {"parentTypeName" "Query"
                                                 "fieldName"      "getEntity"}
                                    "arguments" {"id" "0"}})}))]
    (is (= {"id"         "0",
            "attributes" [{"id"         "10",
                           "name"       ":db/ident",
                           "__typename" "StringAttribute",
                           "string"     ":db.part/db"}
                          {"id"         "11",
                           "name"       ":db.install/partition",
                           "__typename" "MultiReferenceAttribute",
                           "refs"       ["0" "3" "4"]}
                          {"id"         "12",
                           "name"       ":db.install/valueType",
                           "__typename" "MultiReferenceAttribute",
                           "refs"       ["20" "21" "22" "23" "24" "25" "26" "54" "58" "59" "60" "61" "62" "64" "65"]}
                          {"id"         "13",
                           "name"       ":db.install/attribute",
                           "__typename" "MultiReferenceAttribute",
                           "refs"       ["7"
                                         "10"
                                         "11"
                                         "12"
                                         "13"
                                         "15"
                                         "16"
                                         "17"
                                         "18"
                                         "19"
                                         "39"
                                         "40"
                                         "41"
                                         "42"
                                         "43"
                                         "45"
                                         "50"
                                         "51"
                                         "55"
                                         "63"
                                         "66"
                                         "67"
                                         "68"
                                         "69"
                                         "70"
                                         "71"
                                         "72"
                                         "73"
                                         "74"
                                         "75"
                                         "76"
                                         "77"
                                         "78"
                                         "79"
                                         "80"
                                         "81"
                                         "82"]}
                          {"id"         "63",
                           "name"       ":db/doc",
                           "__typename" "StringAttribute",
                           "string"     "Name of the system partition. The system partition includes the core of datomic, as well as user schemas: type definitions, attribute definitions, partition definitions, and data function definitions."}]}
           response))))

(deftest test-entity-browser-list
  (let [conn     (u/temp-conn)
        response (json/read-str
                  (datomic-resolver
                   {:testing-conn conn
                    :input        (json/write-str
                                   {"info"      {"parentTypeName" "Query"
                                                 "fieldName"      "listEntity"}
                                    "arguments" {"page" {"size"   1
                                                         "number" 10000}}})}))]
    (is (= [{"id"         "7",
             "attributes" [{"id"         "10",
                            "name"       ":db/ident",
                            "__typename" "StringAttribute",
                            "string"     ":db/system-tx"}
                           {"id"         "40",
                            "name"       ":db/valueType",
                            "__typename" "ReferenceAttribute",
                            "ref"        "21"}
                           {"id"         "41",
                            "name"       ":db/cardinality",
                            "__typename" "ReferenceAttribute",
                            "ref"        "36"}]}]
           (get response "values")))))