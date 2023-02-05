(ns datomic.schema
  (:require
   [clojure.string :as s]
   [clojure.test :refer [deftest is]]
   [datomic.client.api :as d]
   [shared.attributes :as a]
   [tests :as t]
   [user :as u]))

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
  (-> (->> (disj gql-fields "id")
           (map #(get-in schema [:types gql-type % :graphql.relation/attribute :db/ident]))
           distinct)
      (conj :db/id)))

(deftest gen-pull-pattern-test
  (let [schema  (get-graphql-schema (d/db (t/temp-conn)))
        pattern (gen-pull-pattern t/rel-type #{"id" t/rel-field} schema)]
    (is (= pattern [:db/id t/rel-attribute]))))

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

(defn pull-and-resolve-entity [entity-long-id db gql-type selected-paths schema]
  ; TODO nested fields
  (let [gql-fields (set (filter #(not (s/includes? % "/")) selected-paths))
        pattern    (gen-pull-pattern gql-type gql-fields schema)]
    (->> [entity-long-id]
         (pull-entities db pattern)
         (reverse-pull-pattern gql-type gql-fields schema)
         first)))

(deftest pull-and-resolve-entity-test
  (let [conn           (t/temp-conn)
        added-data     (d/transact conn {:tx-data [{:db/id          "entity id"
                                                    t/rel-attribute t/rel-sample-value}]})
        entity-long-id (get-in added-data [:tempids "entity id"])
        db             (d/db conn)
        selected-paths #{"id" t/rel-field}
        schema         (get-graphql-schema db)
        pulled-entity  (pull-and-resolve-entity entity-long-id db t/rel-type selected-paths schema)]
    (is (= pulled-entity
           {"id"        (str entity-long-id)
            t/rel-field t/rel-sample-value}))))

