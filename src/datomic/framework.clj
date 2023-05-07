(ns datomic.framework
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [datomic.attributes :as attributes]
   [datomic.client.api :as d]
   [datomic.queries :as queries]
   [shared.mappings :as sa]
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
  (->> (vals (get-in schema [:types gql-type]))
       (map (fn [{:keys [graphql.relation/field
                         graphql.relation/target]}]
              (if target
                (str field "/id")
                field)))
       (concat ["id"])))

(comment
  (get-default-paths (get-schema (u/temp-db)) u/test-type-planetary-boundary))

(deftest get-default-paths-test
  (let [schema (get-schema (u/temp-db))
        paths  (get-default-paths schema u/test-type-planetary-boundary)]
    (is (= ["id" u/test-field-name (str u/test-field-quantifications "/id")]
           paths))))

(defn resolve-input-fields [schema input-obj gql-type]
  (let [temp-id   (get input-obj "id")
        uuid      (parse-uuid temp-id)
        input-obj (dissoc input-obj "id")]
    (concat
     ; always add id
     [[:db/add temp-id :platform/id uuid]]
     ; generate adds for input-obj
     (for [[field value] input-obj
           :when (not (nil? value))
           :let [values      (if (sequential? value) value [value])
                 rel         (get-in schema [:types gql-type field])
                 {:keys [graphql.relation/attribute
                         graphql.relation/target
                         graphql.relation/forward?]} rel
                 attr-ident  (:db/ident attribute)
                 target-name (:graphql.type/name target)]
           :when (not (nil? attr-ident))
           value values]
       (if (and target-name (map? value))
         (if forward?
           [:db/add temp-id attr-ident (get value "id")]
           [:db/add (get value "id") attr-ident temp-id])
         [:db/add temp-id attr-ident value]))
     ; generate next level, if available
     (apply
      concat
      (for [[field value] input-obj
            :when (not (nil? value))
            :let [values      (if (sequential? value) value [value])
                  rel         (get-in schema [:types gql-type field])
                  {:keys [graphql.relation/target]} rel
                  target-name (:graphql.type/name target)]
            value values
            :when (and target-name (map? value))]
        (resolve-input-fields schema value target-name))))))

(deftest resolve-input-fields-test
  (let [conn     (u/temp-conn)
        resolved (resolve-input-fields
                  (get-schema (d/db conn))
                  {"id"              "00000000-0000-0000-0000-000000000001"
                   u/test-field-name "PlanetaryBoundary"}
                  u/test-type-planetary-boundary)]
    (d/transact conn {:tx-data resolved})
    (is (= [[:db/add "00000000-0000-0000-0000-000000000001" :platform/id #uuid"00000000-0000-0000-0000-000000000001"]
            [:db/add "00000000-0000-0000-0000-000000000001" u/test-attribute-name "PlanetaryBoundary"]]
           resolved)))

  (is (= [[:db/add "00000000-0000-0000-0000-000000000001" :platform/id #uuid"00000000-0000-0000-0000-000000000001"]
          [:db/add "00000000-0000-0000-0000-000000000001" u/test-attribute-name "PlanetaryBoundary"]
          [:db/add "00000000-0000-0000-0000-000000000001" u/test-attribute-quantifications "00000000-0000-0000-0000-000000000002"]
          [:db/add "00000000-0000-0000-0000-000000000001" u/test-attribute-quantifications "00000000-0000-0000-0000-000000000003"]
          [:db/add "00000000-0000-0000-0000-000000000002" :platform/id #uuid"00000000-0000-0000-0000-000000000002"]
          [:db/add "00000000-0000-0000-0000-000000000002" u/test-attribute-name "Quantification"]
          [:db/add "00000000-0000-0000-0000-000000000003" :platform/id #uuid"00000000-0000-0000-0000-000000000003"]
          [:db/add "00000000-0000-0000-0000-000000000003" u/test-attribute-name "Quantification2"]]
         (resolve-input-fields
          (get-schema (u/temp-db))
          {"id"                         "00000000-0000-0000-0000-000000000001"
           u/test-field-name            "PlanetaryBoundary"
           u/test-field-quantifications [{"id"              "00000000-0000-0000-0000-000000000002"
                                          u/test-field-name "Quantification"}
                                         {"id"              "00000000-0000-0000-0000-000000000003"
                                          u/test-field-name "Quantification2"}]}
          u/test-type-planetary-boundary))))

(defn gen-pull-pattern [schema gql-type gql-fields]
  ; TODO nested selections
  (-> (->> (disj gql-fields "id")
           (map #(get-in schema [:types gql-type % :graphql.relation/attribute :db/ident]))
           distinct)
      (conj :platform/id)))

(deftest gen-pull-pattern-test
  (let [schema  (get-schema (u/temp-db))
        pattern (gen-pull-pattern schema u/test-type-planetary-boundary #{"id" u/test-field-name})]
    (is (= [:platform/id u/test-attribute-name]
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
        {:keys [db-after]} (d/transact conn {:tx-data [{:platform/id          entity-uuid
                                                        u/test-attribute-name u/test-field-name-value-1}]})
        selected-paths #{"id" u/test-field-name}
        schema         (get-schema db-after)
        pulled-entity  (pull-and-resolve-entity-value schema entity-uuid db-after u/test-type-planetary-boundary selected-paths)]
    (is (= {"id"              (str entity-uuid)
            u/test-field-name u/test-field-name-value-1}
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

(comment
  (let [conn (u/temp-conn)
        {:keys [db-after]} (d/transact
                            conn
                            {:tx-data [{:platform/id   (UUID/randomUUID)
                                        :platform/name "123"}
                                       {:platform/id              (UUID/randomUUID)
                                        :platform/name            "456"
                                        :platform/quantifications [{:platform/id   (UUID/randomUUID)
                                                                    :platform/name "abc"}]}]})]
    (get-entities-sorted db-after u/test-type-planetary-boundary)))

(deftest get-entities-sorted-test
  (let [example     (fn [] {:platform/id          (UUID/randomUUID)
                            u/test-attribute-name u/test-field-name-value-1
                            :platform/description "bla"})
        sample-data [(example) (example) (example) (example) (example)]
        conn        (u/temp-conn)
        _           (d/transact
                     conn
                     {:tx-data (attributes/add-value-field-tx-data
                                "tempid"
                                u/test-type-planetary-boundary
                                "description"
                                :platform/description)})]
    (d/transact conn {:tx-data sample-data})
    (is (= (map :platform/id sample-data)
           (get-entities-sorted (d/db conn) u/test-type-planetary-boundary)))
    (is (->> (get-entities-sorted (d/db conn) u/test-type-planetary-boundary)
             (map #(d/pull (d/db conn) '[:db/id] [:platform/id %]))
             (map :db/id)
             (apply <)))))