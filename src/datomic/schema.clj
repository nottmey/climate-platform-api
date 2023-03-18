(ns datomic.schema
  (:require
   [clojure.string :as s]
   [clojure.test :refer [deftest is]]
   [datomic.client.api :as d]
   [datomic.queries :as queries]
   [shared.attributes :as attributes]
   [user :as u]))

(defn get-schema [db]
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
  (get-schema (u/temp-db)))

(defn get-default-paths [schema gql-type]
  ; TODO nested fields
  (-> schema
      (get-in [:types gql-type])
      keys
      set
      (conj "id")
      (conj "session")))

(deftest get-default-paths-test
  (let [schema (get-schema (u/temp-db))
        paths  (get-default-paths schema u/rel-type)]
    (is (contains? paths u/rel-field))))

(defn resolve-input-fields [schema input-obj gql-type]
  ; TODO nested values
  (->> input-obj
       (map
        (fn [[field value]]
          (let [rel (get-in schema [:types gql-type field])
                {:keys [graphql.relation/attribute]} rel]
            [(:db/ident attribute) value])))
       (filter (fn [[ident]] (not (nil? ident))))
       (reduce
        (fn [m [attr value]]
          (if (contains? m attr)
            ; TODO describe cause more in detail (fields + value, not attr)
            (throw (ex-info (str "InputDataConflict: " attr " already set by other input field.") {}))
            (assoc m attr value)))
        {})))

(comment
  (resolve-input-fields
   (get-schema (u/temp-db))
   {"name" "Hello"}
   "PlanetaryBoundary"))

(defn gen-pull-pattern [schema gql-type gql-fields]
  ; TODO nested selections
  (-> (->> (disj gql-fields "id" "session")
           (map #(get-in schema [:types gql-type % :graphql.relation/attribute :db/ident]))
           distinct)
      (conj :db/id)))

(comment
  (gen-pull-pattern (get-schema (u/temp-db)) u/rel-type #{"id" u/rel-field}))

(deftest gen-pull-pattern-test
  (let [schema  (get-schema (u/temp-db))
        pattern (gen-pull-pattern schema u/rel-type #{"id" u/rel-field})]
    (is (= [:db/id u/rel-attribute] pattern))))

(defn reverse-pull-pattern [schema gql-type gql-fields pulled-entities]
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
                         (attributes/->gql-value
                          value
                          (:db/ident (:db/valueType attribute))
                          (:db/ident (:db/cardinality attribute)))])))))
         (into {}))
        (assoc "id" (str (:db/id %))))
   pulled-entities))

(defn pull-and-resolve-entity [schema entity-long-id db gql-type selected-paths]
  ; TODO nested fields
  (let [gql-fields (set (filter #(not (s/includes? % "/")) selected-paths))
        pattern    (gen-pull-pattern schema gql-type gql-fields)]
    (->> [entity-long-id]
         (queries/pull-entities db pattern)
         (reverse-pull-pattern schema gql-type gql-fields)
         first)))

(deftest pull-and-resolve-entity-test
  (let [conn           (u/temp-conn)
        added-data     (d/transact conn {:tx-data [{:db/id          "entity id"
                                                    u/rel-attribute u/rel-sample-value}]})
        entity-long-id (get-in added-data [:tempids "entity id"])
        db             (d/db conn)
        selected-paths #{"id" u/rel-field}
        schema         (get-schema db)
        pulled-entity  (pull-and-resolve-entity schema entity-long-id db u/rel-type selected-paths)]
    (is (= {"id"        (str entity-long-id)
            u/rel-field u/rel-sample-value}
           pulled-entity))))

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
  (let [conn    (u/temp-conn)
        example {u/rel-attribute u/rel-sample-value}]
    (d/transact conn {:tx-data [example example example example example]})
    (get-entities-sorted (d/db conn) u/rel-type)))