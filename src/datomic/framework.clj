(ns datomic.framework
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [datomic.attributes :as attributes]
   [datomic.client.api :as d]
   [datomic.queries :as queries]
   [shared.attributes :as sa]
   [user :as u])
  (:import (java.util UUID)))

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
                  (map first)
                  (sort-by :graphql.relation/field)
                  (sort-by #(:graphql.type/name (:graphql.relation/type %))))]
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
      (conj "id")))

(deftest get-default-paths-test
  (let [schema (get-schema (u/temp-db))
        paths  (get-default-paths schema u/test-type-one)]
    (is (contains? paths u/test-field-one))))

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
            (if (nil? value)
              m
              (assoc m attr value))))
        {:platform/id (parse-uuid (get input-obj "id"))})))

(deftest resolve-input-fields-test
  (let [conn (u/temp-conn)
        {:keys [db-after]} (d/transact
                            conn
                            {:tx-data (attributes/add-value-field-tx-data
                                       "tempid"
                                       u/test-type-one
                                       "description"
                                       :platform/description)})]
    (is (= {:platform/id   #uuid "bdb1df54-7a04-4e24-bc98-6e0dc6a1bdc0"
            :platform/name "Hello"}
           (resolve-input-fields
            (get-schema db-after)
            {"id"          "bdb1df54-7a04-4e24-bc98-6e0dc6a1bdc0"
             "name"        "Hello"
             "description" nil}
            u/test-type-one)))))

(defn gen-pull-pattern [schema gql-type gql-fields]
  ; TODO nested selections
  (-> (->> (disj gql-fields "id")
           (map #(get-in schema [:types gql-type % :graphql.relation/attribute :db/ident]))
           distinct)
      (conj :platform/id)))

(deftest gen-pull-pattern-test
  (let [schema  (get-schema (u/temp-db))
        pattern (gen-pull-pattern schema u/test-type-one #{"id" u/test-field-one})]
    (is (= [:platform/id u/test-attribute-one]
           pattern))))

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
                         (sa/->gql-value
                          value
                          (:db/ident (:db/valueType attribute))
                          (:db/ident (:db/cardinality attribute)))])))))
         (into {}))
        (assoc "id" (str (:platform/id %))))
   pulled-entities))

(defn pull-and-resolve-entity-value [schema entity-uuid db gql-type selected-paths]
  ; TODO nested fields
  ; TODO continue using entity-uuid
  (let [gql-fields (set (filter #(not (str/includes? % "/")) selected-paths))
        pattern    (gen-pull-pattern schema gql-type gql-fields)]
    (->> [entity-uuid]
         (queries/pull-platform-entities db pattern)
         (reverse-pull-pattern schema gql-type gql-fields)
         first)))

(deftest pull-and-resolve-entity-test
  (let [conn           (u/temp-conn)
        entity-uuid    (UUID/randomUUID)
        {:keys [db-after]} (d/transact conn {:tx-data [{:platform/id         entity-uuid
                                                        u/test-attribute-one u/test-field-one-value}]})
        selected-paths #{"id" u/test-field-one}
        schema         (get-schema db-after)
        pulled-entity  (pull-and-resolve-entity-value schema entity-uuid db-after u/test-type-one selected-paths)]
    (is (= {"id"             (str entity-uuid)
            u/test-field-one u/test-field-one-value}
           pulled-entity))))

(defn get-entities-sorted [db type-name]
  (->> (d/q '[:find ?e ?id
              :in $ ?type-name
              :where
              [?type :graphql.type/name ?type-name]
              [?rel :graphql.relation/type ?type]
              [?rel :graphql.relation/attribute ?a]
              [?e ?a]
              [?e :platform/id ?id]]
            db
            type-name)
       (sort-by first)
       (map second)
       (distinct)))

(deftest get-entities-sorted-test
  (let [example     (fn [] {:platform/id          (UUID/randomUUID)
                            u/test-attribute-one  u/test-field-one-value
                            :platform/description "bla"})
        sample-data [(example) (example) (example) (example) (example)]
        conn        (u/temp-conn)
        _           (d/transact
                     conn
                     {:tx-data (attributes/add-value-field-tx-data
                                "tempid"
                                u/test-type-one
                                "description"
                                :platform/description)})]
    (d/transact conn {:tx-data sample-data})
    (is (= (map :platform/id sample-data)
           (get-entities-sorted (d/db conn) u/test-type-one)))
    (is (->> (get-entities-sorted (d/db conn) u/test-type-one)
             (map #(d/pull (d/db conn) '[:db/id] [:platform/id %]))
             (map :db/id)
             (apply <)))))