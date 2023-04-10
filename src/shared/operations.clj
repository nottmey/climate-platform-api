(ns shared.operations
  (:require
   [clojure.set :as set]
   [clojure.string :as s]
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
   [ions.utils :as utils]))

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

(def replace-op
  {:parent-type types/mutation-type
   :prefix      "replace"
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
        replace-op
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

(defn gen-field-name [op entity]
  (str (:prefix op) (name entity)))

(defn gen-graphql-field [op entity fields]
  (let [{:keys [prefix]} op
        field-name (gen-field-name op entity)]
    (case prefix
      "publishCreated" (fields/publish-mutation field-name entity)
      "publishUpdated" (fields/publish-mutation field-name entity)
      "publishDeleted" (fields/publish-mutation field-name entity)
      "get" (fields/get-query field-name entity)
      "list" (fields/list-page-query field-name entity)
      "create" {:name           field-name
                :arguments      [arguments/optional-session
                                 {:name           "value"
                                  :type           (types/input-type entity)
                                  :required-type? true}]
                :type           entity
                :required-type? true}
      "replace" {:name      field-name
                 :arguments [arguments/required-id
                             arguments/optional-session
                             {:name           "value"
                              :type           (types/input-type entity)
                              :required-type? true}]
                 :type      entity}
      "delete" {:name      field-name
                :arguments [arguments/required-id
                            arguments/optional-session]
                :type      entity}
      "onCreated" (fields/subscription field-name entity fields (gen-field-name publish-created-op entity))
      "onUpdated" (fields/subscription field-name entity fields (gen-field-name publish-updated-op entity))
      "onDeleted" (fields/subscription field-name entity fields (gen-field-name publish-deleted-op entity)))))

(defn gen-graphql-object-types [op entity]
  (let [{:keys [prefix]} op]
    (case prefix
      "list" [(objects/list-page entity)]
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
  (s/starts-with? (name field-name) (:prefix op)))

(defn resolve-field [op args]
  (let [{:keys [prefix]} op
        {:keys [conn initial-db schema field-name selected-paths arguments]} args]
    (case prefix
      "get" (let [gql-type  (s/replace (name field-name) prefix "")
                  {:keys [id]} arguments
                  entity-id (parse-long id)]
              {:response (schema/pull-and-resolve-entity schema entity-id initial-db gql-type selected-paths)})
      "list" (let [gql-type   (s/replace (name field-name) prefix "")
                   gql-fields (->> selected-paths
                                   (filter #(s/starts-with? % "values/"))
                                   (map #(s/replace % #"^values/" ""))
                                   (filter #(not (s/includes? % "/")))
                                   set)
                   {:keys [page]} arguments
                   entities   (schema/get-entities-sorted initial-db gql-type)
                   page-info  (utils/page-info page (count entities))
                   pattern    (schema/gen-pull-pattern schema gql-type gql-fields)
                   entities   (->> entities
                                   (drop (get page-info "offset"))
                                   (take (get page-info "size"))
                                   (queries/pull-entities initial-db pattern)
                                   (schema/reverse-pull-pattern schema gql-type gql-fields))]
               {:response {"info"   page-info
                           "values" entities}})
      "create" (let [gql-type      (s/replace (name field-name) prefix "")
                     {:keys [session value]} arguments
                     input         (walk/stringify-keys value)
                     temp-id       "temp-id"
                     input-data    (-> (schema/resolve-input-fields schema input gql-type)
                                       (assoc :db/id temp-id))
                     {:keys [db-after tempids]} (d/transact conn {:tx-data [input-data]})
                     entity-id     (get tempids temp-id)
                     default-paths (schema/get-default-paths schema gql-type)
                     paths         (set/union selected-paths default-paths)
                     entity        (-> (schema/pull-and-resolve-entity schema entity-id db-after gql-type paths)
                                       (assoc "session" session))]
                 {:publish-queries [(create-publish-definition
                                     publish-created-op
                                     gql-type
                                     entity
                                     default-paths)]
                  :response        entity})
      "replace" nil                                         ; TODO
      "delete" (let [gql-type      (s/replace (name field-name) prefix "")
                     {:keys [id session]} arguments
                     entity-id     (parse-long id)
                     default-paths (schema/get-default-paths schema gql-type)
                     paths         (set/union selected-paths default-paths)
                     entity        (schema/pull-and-resolve-entity schema entity-id initial-db gql-type paths)]
                 (if (nil? entity)
                   nil
                   (let [e-with-session (assoc entity "session" session)]
                     (d/transact conn {:tx-data [[:db/retractEntity entity-id]]})
                     {:publish-queries [(create-publish-definition
                                         publish-deleted-op
                                         gql-type
                                         e-with-session
                                         default-paths)]
                      :response        e-with-session}))))))
