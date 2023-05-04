(ns ions.lambdas-test
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [ions.lambdas :refer [datomic-resolver]]
   [user :as u])
  (:import (java.util UUID)))

(defn resolve-input [input]
  (json/read-str
   (datomic-resolver
    {:input (json/write-str input)})))

(deftest test-get-non-existent-id
  (let [response (resolve-input
                  {"info"      {"parentTypeName" "Query"
                                "fieldName"      (str "get" u/test-type-one)}
                   "arguments" {"id" (UUID/randomUUID)}})]
    (is (= response nil))))

(deftest test-list-empty-db
  (let [response (resolve-input
                  {"info"      {"parentTypeName" "Query"
                                "fieldName"      (str "list" u/test-type-one)}
                   "arguments" {}})
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
  (let [entity-id        (.toString (UUID/randomUUID))
        conn             (u/temp-conn)
        publish-called?  (atom false)
        created-response (with-redefs
                          [u/testing-conn    (fn [] conn)
                           u/testing-publish (fn [q]
                                               (reset! publish-called? true)
                                               (let [values (->> (re-seq #"(?s)\".*?\"" q)
                                                                 (map #(str/replace % "\"" "")))
                                                     q-norm (-> (str/replace q #"(?s)\".*?\"" "<v>")
                                                                (str/replace u/test-type-one "<t>")
                                                                (str/replace u/test-field-one "<f1>")
                                                                (str/replace u/test-field-two "<f2>"))]
                                                 (is (= "mutation PublishCreated<t> {\n    publishCreated<t>(value: {id: <v>, <f1>: <v>, <f2>: null}) { id <f1> <f2> } \n}\n\n"
                                                        q-norm))
                                                 (is (= [entity-id u/test-field-one-value-escaped]
                                                        values))))]
                           (resolve-input
                            {"info"      {"parentTypeName"   "Mutation"
                                          "fieldName"        (str "create" u/test-type-one)
                                          "selectionSetList" ["id" u/test-field-one]}
                             "arguments" {"value" {"id"             entity-id
                                                   u/test-field-one u/test-field-one-value}}}))]
    (is (= true @publish-called?))
    (is (= {"id"             entity-id
            u/test-field-one u/test-field-one-value}
           created-response))

    (let [fetched-entity
          (with-redefs [u/testing-conn (fn [] conn)]
            (resolve-input
             {"info"      {"parentTypeName"   "Query"
                           "fieldName"        (str "get" u/test-type-one)
                           "selectionSetList" ["id" u/test-field-one]}
              "arguments" {"id" entity-id}}))]
      (is (= created-response fetched-entity)))

    (let [entity-list
          (with-redefs [u/testing-conn (fn [] conn)]
            (resolve-input
             {"info"      {"parentTypeName"   "Query"
                           "fieldName"        (str "list" u/test-type-one)
                           "selectionSetList" ["values/id" (str "values/" u/test-field-one)]}
              "arguments" {"page" {"size" 10}}}))]
      (is (= {"info"   {"size"    10
                        "offset"  0
                        "first"   0
                        "prev"    nil
                        "current" 0
                        "next"    nil
                        "last"    0}
              "values" [created-response]}
             entity-list)))

    (let [publish-called? (atom false)
          deleted-entity  (with-redefs
                           [u/testing-conn    (fn [] conn)
                            u/testing-publish (fn [q]
                                                (reset! publish-called? true)
                                                (let [values (->> (re-seq #"(?s)\".*?\"" q)
                                                                  (map #(str/replace % "\"" "")))
                                                      q-norm (-> (str/replace q #"(?s)\".*?\"" "<v>")
                                                                 (str/replace u/test-type-one "<t>")
                                                                 (str/replace u/test-field-one "<f1>")
                                                                 (str/replace u/test-field-two "<f2>"))]
                                                  (is (= "mutation PublishDeleted<t> {\n    publishDeleted<t>(value: {id: <v>, <f1>: <v>, <f2>: null}) { id <f1> <f2> } \n}\n\n"
                                                         q-norm))
                                                  (is (= [entity-id u/test-field-one-value-escaped]
                                                         values))))]
                            (resolve-input
                             {"info"      {"parentTypeName"   "Mutation"
                                           "fieldName"        (str "delete" u/test-type-one)
                                           "selectionSetList" ["id" u/test-field-one]}
                              "arguments" {"id" entity-id}}))]
      (is (= created-response deleted-entity))
      (is (= true @publish-called?)))

    (let [fetched-entity
          (with-redefs [u/testing-conn (fn [] conn)]
            (resolve-input
             {"info"      {"parentTypeName"   "Query"
                           "fieldName"        (str "get" u/test-type-one)
                           "selectionSetList" ["id" u/test-field-one]}
              "arguments" {"id" entity-id}}))]
      (is (nil? fetched-entity)))))

(deftest test-entity-browser-get
  (let [response (resolve-input
                  {"info"      {"parentTypeName" "Query"
                                "fieldName"      "getEntity"}
                   "arguments" {"id" "0"}})]
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
                                         "82"
                                         "83"
                                         "84"
                                         "85"]}
                          {"id"         "63",
                           "name"       ":db/doc",
                           "__typename" "StringAttribute",
                           "string"     "Name of the system partition. The system partition includes the core of datomic, as well as user schemas: type definitions, attribute definitions, partition definitions, and data function definitions."}]}
           response))))

(deftest test-entity-browser-list
  (let [response (resolve-input
                  {"info"      {"parentTypeName" "Query"
                                "fieldName"      "listEntity"}
                   "arguments" {"page" {"size"   1
                                        "number" 10000}}})]
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