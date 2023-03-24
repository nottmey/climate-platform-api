(ns ions.lambdas-test
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [ions.lambdas :refer [datomic-resolver]]
   [user :as u]))

(defn resolve-input [input]
  (json/read-str
   (datomic-resolver
    {:input (json/write-str input)})))

(deftest test-get-non-existent-id
  (let [response (resolve-input
                  {"info"      {"parentTypeName" "Query"
                                "fieldName"      (str "get" u/rel-type)}
                   "arguments" {"id" "123"}})]
    (is (= response nil))))

(deftest test-list-empty-db
  (let [response (resolve-input
                  {"info"      {"parentTypeName" "Query"
                                "fieldName"      (str "list" u/rel-type)}
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
  (let [conn            (u/temp-conn)
        publish-called? (atom false)
        created-entity  (with-redefs
                         [u/testing-conn    (fn [] conn)
                          u/testing-publish (fn [q]
                                              (reset! publish-called? true)
                                              (let [[s1 s2 s3] (->> (re-seq #"\".*?\"" q)
                                                                    (map #(str/replace % "\"" "")))
                                                    q-norm (-> (str/replace q #"\".*?\"" "<v>")
                                                               (str/replace u/rel-type "<t>")
                                                               (str/replace u/rel-field "<f>"))]
                                                (is (= q-norm "mutation PublishCreated<t> { publishCreated<t>(id: <v>, session: <v>, value: {<f>: <v>}) { id <f> session } }"))
                                                (is (int? (parse-long s1)))
                                                (is (= "session id" s2))
                                                (is (= u/rel-sample-value s3))))]
                          (resolve-input
                           {"info"      {"parentTypeName"   "Mutation"
                                         "fieldName"        (str "create" u/rel-type)
                                         "selectionSetList" ["id" u/rel-field]}
                            "arguments" {"session" "session id"
                                         "value"   {u/rel-field u/rel-sample-value}}}))
        entity-id       (get created-entity "id")
        created-fetched (dissoc created-entity "session")]
    (is (= u/rel-sample-value (get created-entity u/rel-field)))
    (is (string? entity-id))
    (is (= true @publish-called?))
    (is (= "session id" (get created-entity "session")))

    (let [fetched-entity
          (with-redefs [u/testing-conn (fn [] conn)]
            (resolve-input
             {"info"      {"parentTypeName"   "Query"
                           "fieldName"        (str "get" u/rel-type)
                           "selectionSetList" ["id" u/rel-field]}
              "arguments" {"id" entity-id}}))]
      (is (= created-fetched fetched-entity)))

    (let [entity-list
          (with-redefs [u/testing-conn (fn [] conn)]
            (resolve-input
             {"info"      {"parentTypeName"   "Query"
                           "fieldName"        (str "list" u/rel-type)
                           "selectionSetList" ["values/id" (str "values/" u/rel-field)]}
              "arguments" {"page" {"size" 10}}}))
          expected-list
          {"info"   {"size"    10
                     "offset"  0
                     "first"   0
                     "prev"    nil
                     "current" 0
                     "next"    nil
                     "last"    0}
           "values" [created-fetched]}]
      (is (= expected-list entity-list)))

    (let [publish-called? (atom false)
          deleted-entity  (with-redefs
                           [u/testing-conn    (fn [] conn)
                            u/testing-publish (fn [q]
                                                (reset! publish-called? true)
                                                (let [[s1 s2 s3] (->> (re-seq #"\".*?\"" q)
                                                                      (map #(str/replace % "\"" "")))
                                                      q-norm (-> (str/replace q #"\".*?\"" "<v>")
                                                                 (str/replace u/rel-type "<t>")
                                                                 (str/replace u/rel-field "<f>"))]
                                                  (is (= "mutation PublishDeleted<t> { publishDeleted<t>(id: <v>, session: <v>, value: {<f>: <v>}) { id <f> session } }"
                                                         q-norm))
                                                  (is (int? (parse-long s1)))
                                                  (is (= "session id" s2))
                                                  (is (= u/rel-sample-value s3))))]
                            (resolve-input
                             {"info"      {"parentTypeName"   "Mutation"
                                           "fieldName"        (str "delete" u/rel-type)
                                           "selectionSetList" ["id" u/rel-field]}
                              "arguments" {"id"      entity-id,
                                           "session" "session id"}}))]
      (is (= created-entity deleted-entity))
      (is (= true @publish-called?)))))

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
                                         "82"]}
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