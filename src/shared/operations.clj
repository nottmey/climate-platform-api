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
   [graphql.fields :as fields]
   [graphql.objects :as objects]
   [graphql.spec :as spec]
   [graphql.types :as types]
   [ions.utils :as utils]
   [user :as u])
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
  {::parent-type      types/mutation-type
   ::prefix           "create"
   ::resolver         ::datomic
   ::resolver-options {::requires-id? true}})

(def merge-op
  {::parent-type      types/mutation-type
   ::prefix           "merge"
   ::resolver         ::datomic
   ::resolver-options {::requires-id? true}})

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

(defn all [resolver-location]
  (->> [publish-created-op
        publish-updated-op
        publish-deleted-op
        get-op
        list-op
        create-op
        merge-op
        delete-op
        on-created-op
        on-updated-op
        on-deleted-op]
       (filter #(or (= resolver-location ::any)
                    (= (::resolver %) resolver-location)))))

(deftest all-test
  (is (< 0 (count (all ::any))))
  (is (< 0 (count (all ::datomic))))
  (is (every?
       #(= (::resolver %) ::datomic)
       (all ::datomic))))

(comment
  (all ::any))

(defn gen-field-name [op entity-name]
  (str (::prefix op) (name entity-name)))

(defn gen-entity-name [op field-name]
  (str/replace (name field-name) (::prefix op) ""))

(defn gen-graphql-field [op entity-name fields]
  (let [{:keys [shared.operations/prefix]} op
        field-name (gen-field-name op entity-name)]
    (case prefix
      "publishCreated" (fields/publish-mutation field-name entity-name)
      "publishUpdated" (fields/publish-mutation field-name entity-name)
      "publishDeleted" (fields/publish-mutation field-name entity-name)
      "get" (fields/get-query field-name entity-name)
      "list" (fields/list-page-query field-name entity-name)
      "create" {:name           field-name
                :arguments      [arguments/required-id
                                 (arguments/required-input-value entity-name)]
                :type           entity-name
                :required-type? true}
      "merge" {:name      field-name
               :arguments [arguments/required-id
                           (arguments/required-input-value entity-name)]
               :type      entity-name}
      "delete" {:name      field-name
                :arguments [arguments/required-id]
                :type      entity-name}
      "onCreated" (fields/subscription field-name entity-name fields (gen-field-name publish-created-op entity-name))
      "onUpdated" (fields/subscription field-name entity-name fields (gen-field-name publish-updated-op entity-name))
      "onDeleted" (fields/subscription field-name entity-name fields (gen-field-name publish-deleted-op entity-name)))))

(defn gen-graphql-object-types [op entity-name]
  (let [{:keys [shared.operations/prefix]} op]
    (case prefix
      "list" [(objects/list-page entity-name)]
      nil)))

(defn create-publish-definition [publish-op gql-type entity default-paths]
  (spec/mutation-definition
   {:fields [{:name      (gen-field-name publish-op gql-type)
              :arguments (concat
                          [{:name  :id
                            :value (get entity "id")}]
                          [{:name  :value
                            :value (->> (disj default-paths "id")
                                        (sort)
                                        ; TODO nested values
                                        (map #(vector % (get entity %)))
                                        (into {}))}])
              :selection (->> (sort default-paths)
                              (into []))}]}))

(defn resolves-graphql-field? [op field-name]
  (str/starts-with? (name field-name) (::prefix op)))

(defn graphql-error [^String msg]
  ; TODO find better way to report errors in resolver lambdas for appsync (throwing seems to be the only way)
  ; TODO see whether this looks ok for clients
  (doto (IllegalArgumentException. msg)
    ; https://stackoverflow.com/questions/11434431/exception-without-stack-trace-in-java
    (.setStackTrace (make-array StackTraceElement 0))))

(defn resolve-field [op args]
  (let [{:keys [shared.operations/prefix shared.operations/resolver-options]} op
        {:keys [shared.operations/requires-id?]} resolver-options
        {:keys [conn field-name selected-paths arguments]} args
        {:keys [id page value]} (or arguments {})
        entity-id   (when id (parse-uuid id))
        entity-name (gen-entity-name op field-name)
        db-before   (d/db conn)
        schema      (framework/get-schema db-before)]
    (when (and requires-id? (nil? entity-id))
      (throw (graphql-error "`id` is missing or not a valid UUID")))
    (case prefix
      "get" {:response (framework/pull-and-resolve-entity-value schema entity-id db-before entity-name selected-paths)}
      "list" (let [gql-fields (->> selected-paths
                                   (filter #(str/starts-with? % "values/"))
                                   (map #(str/replace % #"^values/" ""))
                                   (filter #(not (str/includes? % "/")))
                                   set)
                   entities   (framework/get-entities-sorted db-before entity-name)
                   page-info  (utils/page-info page (count entities))
                   pattern    (framework/gen-pull-pattern schema entity-name gql-fields)
                   entities   (->> entities
                                   (drop (get page-info "offset"))
                                   (take (get page-info "size"))
                                   (queries/pull-platform-entities db-before pattern)
                                   (framework/reverse-pull-pattern schema entity-name gql-fields))]
               {:response {"info"   page-info
                           "values" entities}})
      "create" (let [input         (walk/stringify-keys value)
                     input-data    (-> (framework/resolve-input-fields schema input entity-name)
                                       (assoc :platform/id entity-id))
                     {:keys [db-after]} (d/transact conn {:tx-data [input-data]})
                     default-paths (framework/get-default-paths schema entity-name)
                     paths         (set/union selected-paths default-paths)
                     entity-value  (framework/pull-and-resolve-entity-value schema entity-id db-after entity-name paths)]
                 {:publish-queries [(create-publish-definition
                                     publish-created-op
                                     entity-name
                                     entity-value
                                     default-paths)]
                  :response        entity-value})
      "merge" (let [input         (walk/stringify-keys value)
                    input-data    (-> (framework/resolve-input-fields schema input entity-name)
                                      (assoc :db/id [:platform/id entity-id]))
                    ; TODO validate id -> return nil if not present
                    {:keys [db-after]} (d/transact conn {:tx-data [input-data]})
                    default-paths (framework/get-default-paths schema entity-name)
                    paths         (set/union selected-paths default-paths)
                    entity-value  (framework/pull-and-resolve-entity-value schema entity-id db-after entity-name paths)]
                {:publish-queries [(create-publish-definition
                                    publish-updated-op
                                    entity-name
                                    entity-value
                                    default-paths)]
                 :response        entity-value})
      "delete" (let [default-paths (framework/get-default-paths schema entity-name)
                     paths         (set/union selected-paths default-paths)
                     entity-value  (framework/pull-and-resolve-entity-value schema entity-id db-before entity-name paths)]
                 (if (nil? entity-value)
                   nil
                   (do
                     (d/transact conn {:tx-data [[:db/retractEntity [:platform/id entity-id]]]})
                     {:publish-queries [(create-publish-definition
                                         publish-deleted-op
                                         entity-name
                                         entity-value
                                         default-paths)]
                      :response        entity-value}))))))

(deftest resolve-merge-test
  (let [conn        (u/temp-conn)
        entity-uuid (UUID/randomUUID)
        {:keys [tempids]} (d/transact
                           conn
                           {:tx-data [{:db/id          "tempid"
                                       :platform/id    entity-uuid
                                       u/rel-attribute u/rel-sample-value
                                       :db/doc         "other attr value"}]})
        db-id       (get tempids "tempid")
        db-value    (d/pull (d/db conn) '[*] db-id)]
    (is (= {:db/id          db-id
            :platform/id    entity-uuid
            u/rel-attribute u/rel-sample-value
            :db/doc         "other attr value"}
           db-value))
    (let [result       (resolve-field
                        merge-op
                        {:conn       conn
                         :field-name (str "merge" u/rel-type)
                         :arguments  {:id    (str entity-uuid)
                                      :value {u/rel-field "123"}}})
          {:keys [response publish-queries]} result
          new-db-value (d/pull (d/db conn) '[*] db-id)]
      (is (= {"id"        (str entity-uuid)
              u/rel-field "123"}
             response))
      (is (= {:db/id          db-id
              :platform/id    entity-uuid
              u/rel-attribute "123"
              :db/doc         "other attr value"}
             new-db-value))
      (is (= "mutation PublishUpdatedPlanetaryBoundary {\n    publishUpdatedPlanetaryBoundary(id: <id>, value: {name: \"123\"}) { id name } \n}\n\n"
             (-> (first publish-queries)
                 (str/replace (str "\"" entity-uuid "\"") "<id>")))))))
