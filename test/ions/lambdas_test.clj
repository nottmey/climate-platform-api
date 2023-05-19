(ns ions.lambdas-test
  (:require
   [clojure.data.json :as json]
   [clojure.test :refer [deftest is]]
   [graphql.parsing :as gp]
   [ions.lambdas :refer [datomic-resolver]]
   [user :as u])
  (:import (java.util UUID)))

(defn- resolve-input [input]
  (json/read-str
   (datomic-resolver
    {:input (json/write-str input)})))

(defn- expecting-query [publish-called? parsed-query]
  (fn [q]
    (reset! publish-called? true)
    (is (= parsed-query (gp/parse q)))))

(deftest test-get-non-existent-id
  (let [response (resolve-input
                  {"info"      {"parentTypeName" "Query"
                                "fieldName"      (str "get" u/test-type-planetary-boundary)}
                   "arguments" {"id" (UUID/randomUUID)}})]
    (is (= response nil))))

(deftest test-list-empty-db
  (let [response (resolve-input
                  {"info"      {"parentTypeName" "Query"
                                "fieldName"      (str "list" u/test-type-planetary-boundary)}
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
  ; TODO add checking of quantification name
  (let [planetary-boundary-id (.toString (UUID/randomUUID))
        quantification-id     (.toString (UUID/randomUUID))
        conn                  (u/temp-conn)
        publish-called?       (atom false)
        created-response      (with-redefs
                               [u/testing-conn    (fn [] conn)
                                u/testing-publish (expecting-query
                                                   publish-called?
                                                   [{:name      (str "PublishCreated" u/test-type-planetary-boundary)
                                                     :operation :mutation,
                                                     :selection [{:name      (str "publishCreated" u/test-type-planetary-boundary)
                                                                  :arguments [{:name  "value"
                                                                               :value [{:name  "id"
                                                                                        :value planetary-boundary-id}
                                                                                       {:name  u/test-field-name
                                                                                        :value u/test-field-name-value-1}
                                                                                       {:name  u/test-field-quantifications
                                                                                        :value [[{:name  "id"
                                                                                                  :value quantification-id}]]}]}],
                                                                  :selection [{:name "id"}
                                                                              {:name u/test-field-name}
                                                                              {:name      u/test-field-quantifications
                                                                               :selection [{:name "id"}]}]}]}])]
                                (resolve-input
                                 {"info"      {"parentTypeName"   "Mutation"
                                               "fieldName"        (str "create" u/test-type-planetary-boundary)
                                               "selectionSetList" ["id" u/test-field-name (str u/test-field-quantifications "/id")]}
                                  "arguments" {"value" {"id"                         planetary-boundary-id
                                                        u/test-field-name            u/test-field-name-value-1
                                                        u/test-field-quantifications [{"id"              quantification-id
                                                                                       u/test-field-name u/test-field-name-value-2}]}}}))]
    (is (= {"id"                         planetary-boundary-id
            u/test-field-name            u/test-field-name-value-1
            u/test-field-quantifications [{"id" quantification-id}]}
           created-response))
    (is @publish-called?)

    (is (= created-response
           (with-redefs [u/testing-conn (fn [] conn)]
             (resolve-input
              {"info"      {"parentTypeName"   "Query"
                            "fieldName"        (str "get" u/test-type-planetary-boundary)
                            "selectionSetList" ["id" u/test-field-name (str u/test-field-quantifications "/id")]}
               "arguments" {"id" planetary-boundary-id}}))))

    (is (= {"info"   {"size"    10
                      "offset"  0
                      "first"   0
                      "prev"    nil
                      "current" 0
                      "next"    nil
                      "last"    0}
            "values" [created-response]}
           (with-redefs [u/testing-conn (fn [] conn)]
             (resolve-input
              {"info"      {"parentTypeName"   "Query"
                            "fieldName"        (str "list" u/test-type-planetary-boundary)
                            "selectionSetList" ["values/id" (str "values/" u/test-field-name) (str "values/" u/test-field-quantifications "/id")]}
               "arguments" {"page" {"size" 10}}}))))

    (let [publish-called? (atom false)]
      (is (= created-response
             (with-redefs
              [u/testing-conn    (fn [] conn)
               u/testing-publish (expecting-query
                                  publish-called?
                                  [{:name      (str "PublishDeleted" u/test-type-planetary-boundary)
                                    :operation :mutation,
                                    :selection [{:name      (str "publishDeleted" u/test-type-planetary-boundary)
                                                 :arguments [{:name  "value"
                                                              :value [{:name  "id"
                                                                       :value planetary-boundary-id}
                                                                      {:name  u/test-field-name
                                                                       :value u/test-field-name-value-1}
                                                                      {:name  u/test-field-quantifications
                                                                       :value [[{:name  "id"
                                                                                 :value quantification-id}]]}]}],
                                                 :selection [{:name "id"}
                                                             {:name u/test-field-name}
                                                             {:name      u/test-field-quantifications
                                                              :selection [{:name "id"}]}]}]}])]
               (resolve-input
                {"info"      {"parentTypeName"   "Mutation"
                              "fieldName"        (str "delete" u/test-type-planetary-boundary)
                              "selectionSetList" ["id" u/test-field-name]}
                 "arguments" {"id" planetary-boundary-id}}))))
      (is @publish-called?))

    (is (nil? (with-redefs [u/testing-conn (fn [] conn)]
                (resolve-input
                 {"info"      {"parentTypeName"   "Query"
                               "fieldName"        (str "get" u/test-type-planetary-boundary)
                               "selectionSetList" ["id" u/test-field-name]}
                  "arguments" {"id" planetary-boundary-id}}))))))

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
                                         "85"
                                         "86"]}
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