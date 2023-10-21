(ns shared.operations
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [clojure.walk :as walk]
   [datomic.client.api :as d]
   [datomic.framework :as framework]
   [datomic.queries :as queries]
   [graphql.arguments :as arguments]
   [graphql.directives :as directives]
   [graphql.fields :as fields]
   [graphql.objects :as objects]
   [graphql.parsing :as parsing]
   [graphql.spec :as spec]
   [graphql.types :as types]
   [ions.utils :as utils]
   [testing :as t])
  (:import (java.util UUID)))

(def publish-created-op
  {::parent-type      types/mutation-type
   ::prefix           "publishCreated"
   ::resolver         ::js-file
   ::resolver-options {::file         "cdk/publishPipelineResolver.js"
                       ::requires-id? true}})

(def publish-updated-op
  {::parent-type      types/mutation-type
   ::prefix           "publishUpdated"
   ::resolver         ::js-file
   ::resolver-options {::file         "cdk/publishPipelineResolver.js"
                       ::requires-id? true}})

(def publish-deleted-op
  {::parent-type      types/mutation-type
   ::prefix           "publishDeleted"
   ::resolver         ::js-file
   ::resolver-options {::file         "cdk/publishPipelineResolver.js"
                       ::requires-id? true}})

(def get-op
  {::parent-type      types/query-type
   ::prefix           "get"
   ::resolver         ::datomic
   ::resolver-options {::requires-id? true}})

(def list-op
  {::parent-type types/query-type
   ::prefix      "list"
   ::resolver    ::datomic})

(def create-op
  {::parent-type types/mutation-type
   ::prefix      "create"
   ::resolver    ::datomic})

(def merge-op
  {::parent-type types/mutation-type
   ::prefix      "merge"
   ::resolver    ::datomic})

(def delete-op
  {::parent-type      types/mutation-type
   ::prefix           "delete"
   ::resolver         ::datomic
   ::resolver-options {::requires-id? true}})

(def on-created-op
  {::parent-type types/subscription-type
   ::prefix      "onCreated"})

(def on-updated-op
  {::parent-type types/subscription-type
   ::prefix      "onUpdated"})

(def on-deleted-op
  {::parent-type types/subscription-type
   ::prefix      "onDeleted"})

(def all-operations
  [publish-created-op
   publish-updated-op
   publish-deleted-op
   get-op
   list-op
   create-op
   merge-op
   delete-op
   on-created-op
   on-updated-op
   on-deleted-op])

(defn select-datomic-field-operation [parent-type field]
  (->> all-operations
       (filter #(= (::resolver %) ::datomic))
       (filter #(= (name (::parent-type %)) (name parent-type)))
       (filter #(str/starts-with? (name field) (name (::prefix %))))
       first))

(defn gen-field-name [op entity-type]
  (str (::prefix op) (name entity-type)))

(defn extract-entity-type [op field-name]
  (str/replace (name field-name) (::prefix op) ""))

; should return data that the field generation can handle:
#_graphql.spec/field-definition
(defn gen-graphql-field [op entity-type fields]
  (let [{:keys [shared.operations/prefix]} op
        field-name (gen-field-name op entity-type)]
    (case prefix
      "publishCreated" (fields/publish-mutation field-name entity-type)
      "publishUpdated" (fields/publish-mutation field-name entity-type)
      "publishDeleted" (fields/publish-mutation field-name entity-type)
      "get" (fields/get-query field-name entity-type [directives/user-access])
      "list" (fields/list-page-query field-name entity-type nil [directives/user-access])
      "create" {:name           field-name
                :arguments      [(arguments/required-input-value entity-type)]
                :type           entity-type
                :required-type? true
                :directives     [directives/user-access]}
      "merge" {:name       field-name
               :arguments  [(arguments/required-input-value entity-type)]
               :type       entity-type
               :directives [directives/user-access]}
      "delete" {:name       field-name
                :arguments  [arguments/required-id]
                :type       entity-type
                :directives [directives/user-access]}
      "onCreated" (fields/subscription field-name entity-type fields (gen-field-name publish-created-op entity-type))
      "onUpdated" (fields/subscription field-name entity-type fields (gen-field-name publish-updated-op entity-type))
      "onDeleted" (fields/subscription field-name entity-type fields (gen-field-name publish-deleted-op entity-type)))))

(defn gen-graphql-object-types [op entity-type]
  (let [{:keys [shared.operations/prefix]} op]
    (case prefix
      "list" [(objects/list-page entity-type [directives/user-access])]
      nil)))

(defn- convert-to-input [entity paths]
  (->> paths
       (map (fn [[k v]]
              (let [entity-value (get entity k)]
                (cond
                  (nil? entity-value) nil
                  (empty? v) [k entity-value]
                  (sequential? entity-value) [k (map #(convert-to-input % v) entity-value)]
                  :else [k (convert-to-input entity-value v)]))))
       (into {})))

(comment
  (convert-to-input
   {"id" "123"}
   {"id" {},
    "x"  {},
    "y"  {"id" {}}}))

(deftest convert-to-input-test
  (is (= {"id" "123"}
         (convert-to-input
          {"id" "123"}
          {"id" {},
           "x"  {},
           "y"  {"id" {}}})))
  (is (= {"id" "123",
          "x"  "abc",
          "y"  [{"id" "456"}]}
         (convert-to-input
          {"id" "123"
           "x"  "abc"
           "z"  "hidden"
           "y"  [{"id" "456"
                  "z"  "hidden"}]}
          {"id" {},
           "x"  {},
           "y"  {"id" {}}}))))

(defn- convert-to-selection [paths]
  (->> paths
       (walk/postwalk
        (fn [x]
          (if (map? x)
            (->> x (mapcat identity) (filter not-empty))
            x)))))

(deftest convert-to-selection-test
  (is (= ["x" "y" "z"]
         (convert-to-selection {"x" {},
                                "y" {},
                                "z" {}})))
  (is (= ["x" "y" ["id"] "z"]
         (convert-to-selection {"x" {},
                                "y" {"id" {}},
                                "z" {}})))
  (is (= ["x" "y" ["id" "z" ["id"]]]
         (convert-to-selection {"x" {},
                                "y" {"id" {},
                                     "z"  {"id" {}}}}))))

(defn create-publish-definition [publish-op entity-type entity paths]
  (let [prepared-paths (->> paths
                            (map #(str/split % #"/"))
                            (reduce (fn [m segments] (assoc-in m segments {})) {}))]
    (spec/mutation-definition
     {:fields [{:name      (gen-field-name publish-op entity-type)
                :arguments [{:name  :value
                             :value (convert-to-input entity prepared-paths)}]
                :selection (convert-to-selection prepared-paths)}]})))

(deftest create-publish-definition-test
  (let [schema        (framework/get-schema (t/temp-db))
        default-paths (framework/get-default-paths schema "PlanetaryBoundary")]
    (is (= [{:name      "PublishCreatedPlanetaryBoundary",
             :operation :mutation,
             :selection [{:name      "publishCreatedPlanetaryBoundary",
                          :arguments [{:name  "value",
                                       :value [{:name  "id",
                                                :value "123"}
                                               {:name  "name",
                                                :value "n"}
                                               {:name  "quantifications",
                                                :value [[{:name  "id",
                                                          :value "456"}]]}]}],
                          :selection [{:name "description"}
                                      {:name "id"}
                                      {:name "name"}
                                      {:name      "quantifications",
                                       :selection [{:name "id"}]}]}]}]
           (parsing/parse (create-publish-definition
                           publish-created-op
                           "PlanetaryBoundary"
                           {"id"              "123"
                            "name"            "n"
                            "quantifications" [{"id" "456"}]}
                           default-paths))))))

(defn graphql-error [^String msg]
  ; TODO find better way to report errors in resolver lambdas for appsync (throwing seems to be the only way)
  ; TODO see whether this looks ok for clients
  (doto (IllegalArgumentException. msg)
    ; https://stackoverflow.com/questions/11434431/exception-without-stack-trace-in-java
    (.setStackTrace (make-array StackTraceElement 0))))

(defn resolve-dynamic [op args]
  (let [{:keys [shared.operations/prefix shared.operations/resolver-options]} op
        {:keys [shared.operations/requires-id?]} resolver-options
        {:keys [conn field-name selected-paths arguments]} args
        {:keys [id page value]} (or arguments {})
        entity-id   (when id (parse-uuid id))
        entity-type (extract-entity-type op field-name)
        db-before   (d/db conn)
        schema      (framework/get-schema db-before)
        collection  (framework/get-collection schema entity-type)]
    (when (and requires-id? (nil? entity-id))
      (throw (graphql-error "`id` is missing or not a valid UUID")))
    (case prefix
      "get" {:response (framework/pull-and-resolve-entity-value schema entity-id db-before entity-type selected-paths)}
      "list" (let [selected-paths (->> selected-paths
                                       (filter #(str/starts-with? % "values/"))
                                       (map #(str/replace % #"^values/" ""))
                                       set)
                   entities       (queries/get-entities-sorted db-before collection)
                   page-info      (utils/page-info page (count entities))
                   pattern        (framework/gen-pull-pattern schema entity-type selected-paths)
                   entities       (->> entities
                                       (drop (get page-info "offset"))
                                       (take (get page-info "size"))
                                       (queries/pull-platform-entities db-before pattern)
                                       (map #(framework/reverse-pull-pattern schema entity-type selected-paths %)))]
               {:response {"info"   page-info
                           "values" entities}})
      "create" (let [input         (walk/stringify-keys value)
                     entity-id     (parse-uuid (get input "id"))
                     input-data    (framework/resolve-input-fields schema input entity-type)
                     {:keys [db-after]} (d/transact conn {:tx-data input-data})
                     default-paths (framework/get-default-paths schema entity-type)
                     paths         (set/union selected-paths default-paths)
                     entity-value  (framework/pull-and-resolve-entity-value schema entity-id db-after entity-type paths)]
                 ; TODO don't allow creation of entities without any attributes -> would not show up in list
                 {:publish-queries [(create-publish-definition
                                     publish-created-op
                                     entity-type
                                     entity-value
                                     default-paths)]
                  :response        entity-value})
      "merge" (let [input         (walk/stringify-keys value)
                    entity-id     (parse-uuid (get input "id"))
                    input-data    (framework/resolve-input-fields schema input entity-type)
                    ; TODO validate id -> return nil if entity not present
                    {:keys [db-after]} (d/transact conn {:tx-data input-data})
                    default-paths (framework/get-default-paths schema entity-type)
                    paths         (set/union selected-paths default-paths)
                    entity-value  (framework/pull-and-resolve-entity-value schema entity-id db-after entity-type paths)]
                {:publish-queries [(create-publish-definition
                                    publish-updated-op
                                    entity-type
                                    entity-value
                                    default-paths)]
                 :response        entity-value})
      "delete" (let [default-paths (framework/get-default-paths schema entity-type)
                     paths         (set/union selected-paths default-paths)
                     entity-value  (framework/pull-and-resolve-entity-value schema entity-id db-before entity-type paths)]
                 (if (nil? entity-value)
                   nil
                   (do
                     (d/transact conn {:tx-data [[:db/retractEntity [:platform/id entity-id]]]})
                     {:publish-queries [(create-publish-definition
                                         publish-deleted-op
                                         entity-type
                                         entity-value
                                         default-paths)]
                      :response        entity-value}))))))

(deftest resolve-merge-test
  (let [conn         (t/temp-conn)
        entity-uuid  (UUID/randomUUID)
        example-name " :platform/name sample value\n"
        {:keys [tempids]} (d/transact
                           conn
                           {:tx-data [{:db/id         "tempid"
                                       :platform/id   entity-uuid
                                       :platform/name example-name
                                       :db/doc        "other attr value"}]})
        db-id        (get tempids "tempid")
        db-value     (d/pull (d/db conn) '[*] db-id)]
    (is (= {:db/id         db-id
            :platform/id   entity-uuid
            :platform/name example-name
            :db/doc        "other attr value"}
           db-value))
    (let [result       (resolve-dynamic
                        merge-op
                        {:conn       conn
                         :field-name "mergePlanetaryBoundary"
                         :arguments  {:value {"id"   (str entity-uuid)
                                              "name" "123"}}})
          {:keys [response publish-queries]} result
          new-db-value (d/pull (d/db conn) '[*] db-id)]
      (is (= {"id"   (str entity-uuid)
              "name" "123"}
             response))
      (is (= {:db/id         db-id
              :platform/id   entity-uuid
              :platform/name "123"
              :db/doc        "other attr value"}
             new-db-value))
      (is (= [{:name      "PublishUpdatedPlanetaryBoundary"
               :operation :mutation
               :selection [{:arguments [{:name  "value"
                                         :value [{:name  "id"
                                                  :value (str entity-uuid)}
                                                 {:name  "name"
                                                  :value "123"}
                                                 ; TODO nested pull pattern
                                                 #_{:name  "quantifications"
                                                    :value [{:name  "id"
                                                             :value quantification-id}]}]}]
                            :name      "publishUpdatedPlanetaryBoundary"
                            :selection [{:name "description"}
                                        {:name "id"}
                                        {:name "name"}
                                        {:name      "quantifications"
                                         :selection [{:name "id"}]}]}]}]
             (parsing/parse (first publish-queries)))))))
