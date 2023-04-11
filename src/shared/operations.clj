(ns shared.operations
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [clojure.walk :as walk]
   [datomic.client.api :as d]
   [datomic.queries :as queries]
   [datomic.schema :as schema]
   [graphql.arguments :as arguments]
   [graphql.fields :as fields]
   [graphql.objects :as objects]
   [graphql.spec :as spec]
   [graphql.types :as types]
   [ions.utils :as utils]
   [user :as u]))

(def publish-created-op
  {:parent-type   types/mutation-type
   :prefix        "publishCreated"
   :resolver      :js-file
   :resolver-file "cdk/publishPipelineResolver.js"})

(def publish-updated-op
  {:parent-type   types/mutation-type
   :prefix        "publishUpdated"
   :resolver      :js-file
   :resolver-file "cdk/publishPipelineResolver.js"})

(def publish-deleted-op
  {:parent-type   types/mutation-type
   :prefix        "publishDeleted"
   :resolver      :js-file
   :resolver-file "cdk/publishPipelineResolver.js"})

(def get-op
  {:parent-type types/query-type
   :prefix      "get"
   :resolver    :datomic})

(def list-op
  {:parent-type types/query-type
   :prefix      "list"
   :resolver    :datomic})

(def create-op
  {:parent-type types/mutation-type
   :prefix      "create"
   :resolver    :datomic})

(def merge-op
  {:parent-type types/mutation-type
   :prefix      "merge"
   :resolver    :datomic})

(def delete-op
  {:parent-type types/mutation-type
   :prefix      "delete"
   :resolver    :datomic})

(def on-created-op
  {:parent-type types/subscription-type
   :prefix      "onCreated"})

(def on-updated-op
  {:parent-type types/subscription-type
   :prefix      "onUpdated"})

(def on-deleted-op
  {:parent-type types/subscription-type
   :prefix      "onDeleted"})

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
       (filter #(or (= resolver-location :any)
                    (= (:resolver %) resolver-location)))))

(deftest all-test
  (is (< 0 (count (all :any))))
  (is (< 0 (count (all :datomic))))
  (is (every?
       #(= (:resolver %) :datomic)
       (all :datomic))))

(comment
  (all :any))

(defn gen-field-name [op entity-name]
  (str (:prefix op) (name entity-name)))

(defn gen-entity-name [op field-name]
  (str/replace (name field-name) (:prefix op) ""))

(defn gen-graphql-field [op entity-name fields]
  (let [{:keys [prefix]} op
        field-name (gen-field-name op entity-name)]
    (case prefix
      "publishCreated" (fields/publish-mutation field-name entity-name)
      "publishUpdated" (fields/publish-mutation field-name entity-name)
      "publishDeleted" (fields/publish-mutation field-name entity-name)
      "get" (fields/get-query field-name entity-name)
      "list" (fields/list-page-query field-name entity-name)
      "create" {:name           field-name
                :arguments      [arguments/optional-session
                                 (arguments/required-input-value entity-name)]
                :type           entity-name
                :required-type? true}
      "merge" {:name      field-name
               :arguments [arguments/required-id
                           arguments/optional-session
                           (arguments/required-input-value entity-name)]
               :type      entity-name}
      "delete" {:name      field-name
                :arguments [arguments/required-id
                            arguments/optional-session]
                :type      entity-name}
      "onCreated" (fields/subscription field-name entity-name fields (gen-field-name publish-created-op entity-name))
      "onUpdated" (fields/subscription field-name entity-name fields (gen-field-name publish-updated-op entity-name))
      "onDeleted" (fields/subscription field-name entity-name fields (gen-field-name publish-deleted-op entity-name)))))

(defn gen-graphql-object-types [op entity-name]
  (let [{:keys [prefix]} op]
    (case prefix
      "list" [(objects/list-page entity-name)]
      nil)))

(defn create-publish-definition [publish-op gql-type entity default-paths]
  (spec/mutation-definition
   {:fields [{:name      (gen-field-name publish-op gql-type)
              :arguments (concat
                          [{:name  :id
                            :value (get entity "id")}]
                          (when-let [session (get entity "session")]
                            [{:name  :session
                              :value session}])
                          [{:name  :value
                            :value (->> (disj default-paths "id" "session")
                                        (sort)
                                        ; TODO nested values
                                        (map #(vector % (get entity %)))
                                        (into {}))}])
              :selection (->> (sort default-paths)
                              (into []))}]}))

(defn resolves-graphql-field? [op field-name]
  (str/starts-with? (name field-name) (:prefix op)))

(defn resolve-field [op args]
  (let [{:keys [prefix]} op
        {:keys [conn field-name selected-paths arguments]} args
        {:keys [id page session value]} (or arguments {})
        entity-id   (when id (parse-long id))
        entity-name (gen-entity-name op field-name)
        db-before   (d/db conn)
        schema      (schema/get-schema db-before)]
    (case prefix
      "get" {:response (schema/pull-and-resolve-entity-value schema entity-id db-before entity-name selected-paths)}
      "list" (let [gql-fields (->> selected-paths
                                   (filter #(str/starts-with? % "values/"))
                                   (map #(str/replace % #"^values/" ""))
                                   (filter #(not (str/includes? % "/")))
                                   set)
                   entities   (schema/get-entities-sorted db-before entity-name)
                   page-info  (utils/page-info page (count entities))
                   pattern    (schema/gen-pull-pattern schema entity-name gql-fields)
                   entities   (->> entities
                                   (drop (get page-info "offset"))
                                   (take (get page-info "size"))
                                   (queries/pull-entities db-before pattern)
                                   (schema/reverse-pull-pattern schema entity-name gql-fields))]
               {:response {"info"   page-info
                           "values" entities}})
      "create" (let [input         (walk/stringify-keys value)
                     temp-id       "temp-id"
                     input-data    (-> (schema/resolve-input-fields schema input entity-name)
                                       (assoc :db/id temp-id))
                     {:keys [db-after tempids]} (d/transact conn {:tx-data [input-data]})
                     entity-id     (get tempids temp-id)
                     default-paths (schema/get-default-paths schema entity-name)
                     paths         (set/union selected-paths default-paths)
                     entity-value  (-> (schema/pull-and-resolve-entity-value schema entity-id db-after entity-name paths)
                                       (assoc "session" session))]
                 {:publish-queries [(create-publish-definition
                                     publish-created-op
                                     entity-name
                                     entity-value
                                     default-paths)]
                  :response        entity-value})
      "merge" (let [input         (walk/stringify-keys value)
                    input-data    (-> (schema/resolve-input-fields schema input entity-name)
                                      (assoc :db/id entity-id))
                    ; TODO validate id to be not nil -> error reporting
                    {:keys [db-after]} (d/transact conn {:tx-data [input-data]})
                    default-paths (schema/get-default-paths schema entity-name)
                    paths         (set/union selected-paths default-paths)
                    entity-value  (-> (schema/pull-and-resolve-entity-value schema entity-id db-after entity-name paths)
                                      (assoc "session" session))]
                {:publish-queries [(create-publish-definition
                                    publish-updated-op
                                    entity-name
                                    entity-value
                                    default-paths)]
                 :response        entity-value})
      "delete" (let [default-paths (schema/get-default-paths schema entity-name)
                     paths         (set/union selected-paths default-paths)
                     entity-value  (schema/pull-and-resolve-entity-value schema entity-id db-before entity-name paths)]
                 (if (nil? entity-value)
                   nil
                   (let [e-with-session (assoc entity-value "session" session)]
                     (d/transact conn {:tx-data [[:db/retractEntity entity-id]]})
                     {:publish-queries [(create-publish-definition
                                         publish-deleted-op
                                         entity-name
                                         e-with-session
                                         default-paths)]
                      :response        e-with-session}))))))

(deftest resolve-merge-test
  (let [conn         (u/temp-conn)
        {:keys [tempids]} (d/transact
                           conn
                           {:tx-data [{:db/id          "tempid"
                                       u/rel-attribute u/rel-sample-value
                                       :db/doc         "other attr value"}]})
        entity-id    (get tempids "tempid")
        entity-value (d/pull (d/db conn) '[*] entity-id)]
    (is (= {:db/id          entity-id
            u/rel-attribute u/rel-sample-value
            :db/doc         "other attr value"}
           entity-value))
    (let [result    (resolve-field
                     merge-op
                     {:conn       conn
                      :field-name (str "merge" u/rel-type)
                      :arguments  {:id      (str entity-id)
                                   :session "device-1"
                                   :value   {u/rel-field "123"}}})
          {:keys [response publish-queries]} result
          new-value (d/pull (d/db conn) '[*] entity-id)]
      (is (= {"id"        (str entity-id)
              "session"   "device-1"
              u/rel-field "123"}
             response))
      (is (= {:db/id          entity-id
              u/rel-attribute "123"
              :db/doc         "other attr value"}
             new-value))
      (is (= "mutation PublishUpdatedPlanetaryBoundary { publishUpdatedPlanetaryBoundary(id: \"<id>\", session: \"device-1\", value: {name: \"123\"}) { id name session } }"
             (-> (first publish-queries)
                 (str/replace (str entity-id) "<id>")))))))
