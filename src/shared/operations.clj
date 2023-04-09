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

(defn create-publish-definition [publish-op gql-type entity default-paths]
  (let [field-name (:name ((:gen-graphql-field publish-op) (:prefix publish-op) gql-type {}))]
    (spec/mutation-definition
     {:fields [{:name      field-name
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
                                (into []))}]})))

(def publish-created-op
  {:parent-type       types/mutation-type
   :prefix            "publishCreated"
   :resolver          :js-file
   :resolver-file     "cdk/publishPipelineResolver.js"
   ; TODO extract into functions:
   :gen-graphql-field (fn [prefix entity _]
                        (fields/publish-mutation prefix entity))})

(def publish-deleted-op
  {:parent-type       types/mutation-type
   :prefix            "publishDeleted"
   :resolver          :js-file
   :resolver-file     "cdk/publishPipelineResolver.js"
   ; TODO extract into functions:
   :gen-graphql-field (fn [prefix entity _]
                        (fields/publish-mutation prefix entity))})

(def publish-updated-op
  {:parent-type       types/mutation-type
   :prefix            "publishUpdated"
   :resolver          :js-file
   :resolver-file     "cdk/publishPipelineResolver.js"
   ; TODO extract into functions:
   :gen-graphql-field (fn [prefix entity _]
                        (fields/publish-mutation prefix entity))})

(def get-op
  {:parent-type        types/query-type
   :prefix             "get"
   :resolver           :datomic
   ; TODO extract into functions:
   :gen-graphql-field  (fn [_ entity _]
                         (fields/get-query entity))
   :resolve-field-data (fn [prefix {:keys [initial-db schema field-name selected-paths arguments]}]
                         (let [gql-type  (s/replace (name field-name) prefix "")
                               {:keys [id]} arguments
                               entity-id (parse-long id)]
                           {:response (schema/pull-and-resolve-entity schema entity-id initial-db gql-type selected-paths)}))})

(def list-op
  {:parent-type              types/query-type
   :prefix                   "list"
   :resolver                 :datomic
   ; TODO extract into functions:
   :gen-graphql-field        (fn [_ entity _]
                               (fields/list-page-query entity))
   :gen-graphql-object-types (fn [entity]
                               [(objects/list-page entity)])
   :resolve-field-data       (fn [prefix {:keys [initial-db schema field-name selected-paths arguments]}]
                               (let [gql-type   (s/replace (name field-name) prefix "")
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
                                             "values" entities}}))})

(def create-op
  {:parent-type        types/mutation-type
   :prefix             "create"
   :resolver           :datomic
   ; TODO extract into functions:
   :gen-graphql-field  (fn [prefix entity _]
                         {:name           (str prefix (name entity))
                          :arguments      [arguments/optional-session
                                           {:name           "value"
                                            :type           (types/input-type entity)
                                            :required-type? true}]
                          :type           entity
                          :required-type? true})
   :resolve-field-data (fn [prefix {:keys [conn schema field-name selected-paths arguments]}]
                         (let [gql-type      (s/replace (name field-name) prefix "")
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
                            :response        entity}))})

(def replace-op
  {:parent-type        types/mutation-type
   :prefix             "replace"
   :resolver           :datomic
   ; TODO extract into functions:
   :gen-graphql-field  (fn [prefix entity _]
                         {:name      (str prefix (name entity))
                          :arguments [arguments/required-id
                                      arguments/optional-session
                                      {:name           "value"
                                       :type           (types/input-type entity)
                                       :required-type? true}]
                          :type      entity})
   :resolve-field-data (fn [_prefix _args]
                         ; TODO implement replace resolver
                         )})

(def delete-op
  {:parent-type        types/mutation-type
   :prefix             "delete"
   :resolver           :datomic
   ; TODO extract into functions:
   :gen-graphql-field  (fn [prefix entity _]
                         {:name      (str prefix (name entity))
                          :arguments [arguments/required-id
                                      arguments/optional-session]
                          :type      entity})
   :resolve-field-data (fn [prefix {:keys [conn initial-db schema field-name selected-paths arguments]}]
                         (let [gql-type      (s/replace (name field-name) prefix "")
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
                                :response        e-with-session}))))})

(def on-created-op
  {:parent-type       types/subscription-type
   :prefix            "onCreated"
   ; TODO extract into functions:
   :gen-graphql-field (fn [prefix entity fields]
                        (fields/subscription prefix entity fields publish-created-op))})

(def on-updated-op
  {:parent-type       types/subscription-type
   :prefix            "onUpdated"
   ; TODO extract into functions:
   :gen-graphql-field (fn [prefix entity fields]
                        (fields/subscription prefix entity fields publish-updated-op))})

(def on-deleted-op
  {:parent-type       types/subscription-type
   :prefix            "onDeleted"
   ; TODO extract into functions:
   :gen-graphql-field (fn [prefix entity fields]
                        (fields/subscription prefix entity fields publish-deleted-op))})

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

(defn resolves-graphql-field? [op field-name]
  (s/starts-with? (name field-name) (:prefix op)))
