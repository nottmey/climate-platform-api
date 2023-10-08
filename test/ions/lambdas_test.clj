(ns ions.lambdas-test
  (:require
   [clojure.data.json :as json]
   [clojure.test :refer [deftest is]]
   [graphql.parsing :as parsing]
   [ions.lambdas :refer [datomic-resolver]]
   [user :as u]
   [utils.cloud-import :refer [call-resolver-with-local-conn]])
  (:import (java.util UUID)))

(defn- resolve-input [input]
  (json/read-str
   (datomic-resolver
    {:input (json/write-str input)})))

(defn- expecting-query [publish-called? parsed-query]
  (fn [q]
    (reset! publish-called? true)
    (is (= parsed-query (parsing/parse q)))))

(deftest get-non-existent-id-test
  (is
   (nil?
    (call-resolver-with-local-conn
     "Query"
     "getPlanetaryBoundary"
     ["id"]
     {"id" (str (UUID/randomUUID))}))))

; TODO transition tests in file to resolver
(deftest test-list-empty-db
  (let [response (resolve-input
                  {"info"      {"parentTypeName" "Query"
                                "fieldName"      "listPlanetaryBoundary"}
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
        example-name          " :platform/name sample value\n"
        conn                  (u/temp-conn)
        publish-called?       (atom false)
        created-response      (with-redefs
                               [u/testing-conn    (fn [] conn)
                                u/testing-publish (expecting-query
                                                   publish-called?
                                                   [{:name      "PublishCreatedPlanetaryBoundary"
                                                     :operation :mutation,
                                                     :selection [{:name      "publishCreatedPlanetaryBoundary"
                                                                  :arguments [{:name  "value"
                                                                               :value [{:name  "id"
                                                                                        :value planetary-boundary-id}
                                                                                       {:name  "name"
                                                                                        :value example-name}
                                                                                       {:name  "quantifications"
                                                                                        :value [[{:name  "id"
                                                                                                  :value quantification-id}]]}]}],
                                                                  :selection [{:name "description"}
                                                                              {:name "id"}
                                                                              {:name "name"}
                                                                              {:name      "quantifications"
                                                                               :selection [{:name "id"}]}]}]}])]
                                (resolve-input
                                 {"info"      {"parentTypeName"   "Mutation"
                                               "fieldName"        "createPlanetaryBoundary"
                                               "selectionSetList" ["id" "name" "quantifications/id"]}
                                  "arguments" {"value" {"id"              planetary-boundary-id
                                                        "name"            example-name
                                                        "quantifications" [{"id"   quantification-id
                                                                            "name" " some other \n value"}]}}}))]
    (is (= {"id"              planetary-boundary-id
            "name"            example-name
            "quantifications" [{"id" quantification-id}]}
           created-response))
    (is @publish-called?)

    (is (= created-response
           (with-redefs [u/testing-conn (fn [] conn)]
             (resolve-input
              {"info"      {"parentTypeName"   "Query"
                            "fieldName"        "getPlanetaryBoundary"
                            "selectionSetList" ["id" "name" "quantifications/id"]}
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
                            "fieldName"        "listPlanetaryBoundary"
                            "selectionSetList" ["values/id" "values/name" "values/quantifications/id"]}
               "arguments" {"page" {"size" 10}}}))))

    (let [publish-called? (atom false)]
      (is (= created-response
             (with-redefs
              [u/testing-conn    (fn [] conn)
               u/testing-publish (expecting-query
                                  publish-called?
                                  [{:name      "PublishDeletedPlanetaryBoundary"
                                    :operation :mutation,
                                    :selection [{:name      "publishDeletedPlanetaryBoundary"
                                                 :arguments [{:name  "value"
                                                              :value [{:name  "id"
                                                                       :value planetary-boundary-id}
                                                                      {:name  "name"
                                                                       :value example-name}
                                                                      {:name  "quantifications"
                                                                       :value [[{:name  "id"
                                                                                 :value quantification-id}]]}]}],
                                                 :selection [{:name "description"}
                                                             {:name "id"}
                                                             {:name "name"}
                                                             {:name      "quantifications"
                                                              :selection [{:name "id"}]}]}]}])]
               (resolve-input
                {"info"      {"parentTypeName"   "Mutation"
                              "fieldName"        "deletePlanetaryBoundary"
                              "selectionSetList" ["id" "name"]}
                 "arguments" {"id" planetary-boundary-id}}))))
      (is @publish-called?))

    (is (nil? (with-redefs [u/testing-conn (fn [] conn)]
                (resolve-input
                 {"info"      {"parentTypeName"   "Query"
                               "fieldName"        "getPlanetaryBoundary"
                               "selectionSetList" ["id" "name"]}
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
                           "refs"       ["...many..."]}
                          {"id"         "63",
                           "name"       ":db/doc",
                           "__typename" "StringAttribute",
                           "string"     "Name of the system partition. The system partition includes the core of datomic, as well as user schemas: type definitions, attribute definitions, partition definitions, and data function definitions."}]}
           (-> response
               (assoc-in ["attributes" 3 "refs"] ["...many..."]))))))

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
