(ns ions.lambdas-test
  (:require
   [clojure.data.json :as json]
   [clojure.string :as s]
   [clojure.test :refer [deftest is]]
   [ions.lambdas :refer [datomic-resolver]]
   [user :as u]))

(deftest test-get-non-existent-id
  (let [conn     (u/temp-conn)
        response (json/read-str
                  (datomic-resolver
                   {:testing-conn    conn
                    :testing-publish (fn [_] (throw (AssertionError.)))
                    :input           (json/write-str
                                      {"info"      {"parentTypeName" "Query"
                                                    "fieldName"      (str "get" u/rel-type)}
                                       "arguments" {"id" "123"}})}))]
    (is (= response nil))))

(deftest test-list-empty-db
  (let [conn     (u/temp-conn)
        response (json/read-str
                  (datomic-resolver
                   {:testing-conn    conn
                    :testing-publish (fn [_] (throw (AssertionError.)))
                    :input           (json/write-str
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
  (let [conn            (u/temp-conn)
        publish-called? (atom false)
        created-entity  (json/read-str
                         (datomic-resolver
                          {:testing-conn    conn
                           :testing-publish (fn [q]
                                              (reset! publish-called? true)
                                              (let [[q1 id q2 value q3] (s/split q #"\"")]
                                                (is (= (str "mutation PublishCreated"
                                                            u/rel-type
                                                            " { publishCreated"
                                                            u/rel-type
                                                            "(id: ")
                                                       q1))
                                                (is (int? (parse-long id)))
                                                (is (= (str ", value: {" u/rel-field ": ") q2))
                                                (is (= u/rel-sample-value value))
                                                (is (= (str "}) { id " u/rel-field " } }") q3))))
                           :input           (json/write-str
                                             {"info"      {"parentTypeName"   "Mutation"
                                                           "fieldName"        (str "create" u/rel-type)
                                                           "selectionSetList" ["id" u/rel-field]}
                                              "arguments" {"value" {u/rel-field u/rel-sample-value}}})}))
        entity-id       (get created-entity "id")]
    (is (= u/rel-sample-value (get created-entity u/rel-field)))
    (is (string? entity-id))
    #_(is (= true @publish-called?))

    (let [fetched-entity
          (json/read-str
           (datomic-resolver
            {:testing-conn    conn
             :testing-publish (fn [_] (throw (AssertionError.)))
             :input           (json/write-str
                               {"info"      {"parentTypeName"   "Query"
                                             "fieldName"        (str "get" u/rel-type)
                                             "selectionSetList" ["id" u/rel-field]}
                                "arguments" {"id" entity-id}})}))]
      (is (= created-entity fetched-entity)))

    (let [entity-list
          (json/read-str
           (datomic-resolver
            {:testing-conn    conn
             :testing-publish (fn [_] (throw (AssertionError.)))
             :input           (json/write-str
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

    (let [publish-called? (atom false)
          deleted-entity  (json/read-str
                           (datomic-resolver
                            {:testing-conn    conn
                             :testing-publish (fn [q]
                                                (reset! publish-called? true)
                                                (let [[q1 id q2 value q3] (s/split q #"\"")]
                                                  (is (= (str "mutation PublishDeleted"
                                                              u/rel-type
                                                              " { publishDeleted"
                                                              u/rel-type
                                                              "(id: ")
                                                         q1))
                                                  (is (int? (parse-long id)))
                                                  (is (= (str ", value: {" u/rel-field ": ") q2))
                                                  (is (= u/rel-sample-value value))
                                                  (is (= (str "}) { id " u/rel-field " } }") q3))))
                             :input           (json/write-str
                                               {"info"      {"parentTypeName"   "Mutation"
                                                             "fieldName"        (str "delete" u/rel-type)
                                                             "selectionSetList" ["id" u/rel-field]}
                                                "arguments" {"id" entity-id}})}))]
      (is (= created-entity deleted-entity))
      #_(is (= true @publish-called?)))))

(deftest test-entity-browser-get
  (let [conn     (u/temp-conn)
        response (json/read-str
                  (datomic-resolver
                   {:testing-conn    conn
                    :testing-publish (fn [_] (throw (AssertionError.)))
                    :input           (json/write-str
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
                   {:testing-conn    conn
                    :testing-publish (fn [_] (throw (AssertionError.)))
                    :input           (json/write-str
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