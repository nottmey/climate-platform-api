(ns datomic.schema
  (:require
    [clojure.string :as s]
    [datomic.client.api :as d]
    [shared.attributes :as a]
    [user :as u]))

; TODO use transaction function to ensure type is complete

(defn graphql-doc [text]
  (str "Stored GraphQL Schema: " text))

(def graphql-attributes
  [{:db/ident       :graphql.type/name
    :db/unique      :db.unique/identity
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         (graphql-doc "Name of this GraphQL type.")}
   {:db/ident       :graphql.type/deprecated?
    :db/valueType   :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db/doc         (graphql-doc "Whether this GraphQL type is deprecated or not. Default: `false`.")}
   {:db/ident       :graphql.relation/deprecated?
    :db/valueType   :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db/doc         (graphql-doc "Whether the GraphQL field of this GraphQL-field-to-Datomic-attribute relation is deprecated or not. Default: `false`.")}
   {:db/ident       :graphql.relation/type
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc         (graphql-doc "GraphQL type in which the GraphQL field of this GraphQL-field-to-Datomic-attribute relation is contained.")}
   {:db/ident       :graphql.relation/field
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         (graphql-doc "Field name of this GraphQL-field-to-Datomic-attribute relation.")}
   {:db/ident       :graphql.relation/type+field
    :db/unique      :db.unique/identity
    :db/valueType   :db.type/tuple
    :db/tupleAttrs  [:graphql.relation/type
                     :graphql.relation/field]
    :db/cardinality :db.cardinality/one
    :db/doc         (graphql-doc "Unique automatically managed GraphQL-type-and-field-name tuple of this GraphQL-field-to-Datomic-attribute relation.")}
   {:db/ident       :graphql.relation/target
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc         (graphql-doc "GraphQL type which is targeted by this GraphQL-field-to-Datomic-attribute relation, iff it is a reference to another entity.")}
   {:db/ident       :graphql.relation/attribute
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc         (graphql-doc "Datomic attribute which is pulled, when this relations type and field is requested in the GraphQL API.")}
   {:db/ident       :graphql.relation/forward?
    :db/valueType   :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db/doc         (graphql-doc "Whether the forward or backwards reference of this relations reference attribute should be pulled, when requested in the GraphQL API. Default: `true`.")}])

(comment
  (u/ensure-schema
    graphql-attributes
    #_access/dev-env-db-name))

(def platform-attributes
  [{:db/ident       :platform/name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         "Name of any non-user platform entity."}])

(comment
  (u/ensure-schema
    platform-attributes
    #_access/dev-env-db-name))

(defn add-value-field-tx-data [type-name field-name attribute]
  [{:db/id             type-name
    :graphql.type/name type-name}
   {:graphql.relation/attribute attribute
    :graphql.relation/type      type-name
    :graphql.relation/field     field-name}])

(comment
  (u/ensure-data
    (add-value-field-tx-data "PlanetaryBoundary" "name" :platform/name)
    #_access/dev-env-db-name))

(defn add-ref-field-tx-data [type-name field-name target-type attribute forward?]
  [{:db/id             type-name
    :graphql.type/name type-name}
   {:graphql.relation/attribute attribute
    :graphql.relation/forward?  forward?
    :graphql.relation/type      type-name
    :graphql.relation/field     field-name
    :graphql.relation/target    target-type}])

(comment
  (d/transact
    (u/sandbox-conn)
    {:tx-data (add-ref-field-tx-data "SomeType" "refToX" "SomeType" :db/cardinality true)}))

(defn deprecate-type-tx-data [type-name]
  [{:graphql.type/name        type-name
    :graphql.type/deprecated? true}])

(comment
  (d/transact
    (u/sandbox-conn)
    {:tx-data (deprecate-type-tx-data "SomeType")}))

(defn deprecate-type-field-tx-data [type-name field-name]
  ; FIXME doesn't work like this, always creates new relation entities
  [{:db/id             type-name
    :graphql.type/name type-name}
   {:graphql.relation/type+field  [type-name field-name]
    :graphql.relation/deprecated? true}])

(comment
  (d/transact
    (u/sandbox-conn)
    {:tx-data (deprecate-type-field-tx-data "SomeType" "refToX")}))

(defn get-graphql-schema [db]
  (let [base (->> (d/q '[:find (pull ?rel [:graphql.relation/field
                                           :graphql.relation/forward?
                                           :graphql.relation/deprecated?
                                           {:graphql.relation/target [:graphql.type/name
                                                                      :graphql.type/deprecated?]}
                                           {:graphql.relation/attribute [:db/ident
                                                                         {:db/valueType [:db/ident]}
                                                                         {:db/cardinality [:db/ident]}]}
                                           {:graphql.relation/type [:graphql.type/name
                                                                    :graphql.type/deprecated?]}])
                         :in $
                         :where
                         [?rel :graphql.relation/type]
                         [?rel :graphql.relation/attribute]
                         [?rel :graphql.relation/field]]
                       db)
                  (map first))]
    {:list       base
     :types      (-> (group-by #(get-in % [:graphql.relation/type :graphql.type/name]) base)
                     (update-vals #(-> (group-by (fn [{:keys [graphql.relation/field]}] field) %)
                                       (update-vals (fn [relations]
                                                      (assert (= (count relations) 1)
                                                              (str "There must only be one relation per Type-field tuple. " relations))
                                                      (first relations))))))
     :attributes (-> (group-by #(get-in % [:graphql.relation/attribute :db/ident]) base)
                     (update-vals #(group-by (fn [{:keys [graphql.relation/type]}] (:graphql.type/name type)) %)))}))

(comment
  (let [db (u/sandbox-db)]
    (time (get-graphql-schema db))))

(defn get-graphql-types [db]
  (-> (get-graphql-schema db)
      :types
      keys))

(comment
  (let [db (u/sandbox-db)]
    (time (get-graphql-types db))))

(defn resolve-input-fields [input-obj gql-type schema]
  ; TODO nested values
  (->> input-obj
       (map
         (fn [[field value]]
           (let [rel (get-in schema [:types gql-type field])
                 {:keys [graphql.relation/attribute]} rel]
             [(:db/ident attribute) value])))
       (reduce
         (fn [m [attr value]]
           (if (contains? m attr)
             ; TODO describe cause more in detail (fields + value, not attr)
             (throw (ex-info (str "InputDataConflict: " attr " already set by other input field.") {}))
             (assoc m attr value)))
         {})))

(comment
  (let [db     (u/sandbox-db)
        schema (get-graphql-schema db)]
    (time (resolve-input-fields {"name" "Hello"} "PlanetaryBoundary" schema))))

(defn get-entities-sorted [db type-name]
  (->> (d/q '[:find (min ?t) ?e
              :in $ ?type-name
              :where
              [?type :graphql.type/name ?type-name]
              [?rel :graphql.relation/type ?type]
              [?rel :graphql.relation/attribute ?a]
              [?e ?a _ ?t]]
            db
            type-name)
       (sort-by first)
       (map last)))

(comment
  (let [db (u/sandbox-db)]
    (time (get-entities-sorted db "PlanetaryBoundary"))))

(defn gen-pull-pattern [gql-type gql-fields schema]
  ; TODO nested selections
  (-> (->> gql-fields
           (map #(get-in schema [:types gql-type % :graphql.relation/attribute :db/ident]))
           distinct)
      (conj :db/id)))

(defn pull-entities [db pattern entities]
  (->> (map-indexed vector entities)
       (d/q '[:find ?idx (pull ?e pattern)
              :in $ pattern [[?idx ?e]]
              :where [?e]] db pattern)
       (sort-by first)
       (map second)))

(comment
  (let [db (u/sandbox-db)]
    (time (pull-entities db [:db/id :platform/name] [92358976733295 123 87960930222192])))

  (let [db (u/sandbox-db)]
    (time (pull-entities db '[*] [123]))))

(defn reverse-pull-pattern [gql-type gql-fields schema pulled-entities]
  ; TODO reverse nested values
  (map
    #(-> (->>
           %
           (mapcat
             (fn [[key value]]
               (->> (get-in schema [:attributes key gql-type])
                    (filter (fn [{:keys [graphql.relation/field]}]
                              (contains? gql-fields field)))
                    (map (fn [{:keys [graphql.relation/field
                                      graphql.relation/attribute]}]
                           [field
                            (a/->gql-value
                              value
                              (:db/ident (:db/valueType attribute))
                              (:db/ident (:db/cardinality attribute)))])))))
           (into {}))
         (assoc "id" (str (:db/id %))))
    pulled-entities))

(defn pull-and-resolve-entity [entity-id db gql-type selected-paths schema]
  ; TODO nested fields
  (let [gql-fields (set (filter #(not (s/includes? % "/")) selected-paths))
        pattern    (gen-pull-pattern gql-type gql-fields schema)]
    (->> [entity-id]
         (pull-entities db pattern)
         (reverse-pull-pattern gql-type gql-fields schema)
         first)))
