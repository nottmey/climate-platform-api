(ns datomic.framework
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [datomic.client.api :as d]
   [datomic.queries :as queries]
   [shared.mappings :as sa]
   [user :as u])
  (:import (java.util Set UUID)))

(defn get-schema [db]
  (let [base (->> (d/q '[:find (pull ?type [* {:graphql.type/fields [*
                                                                     {:graphql.field/attribute [*]}
                                                                     {:graphql.field/target [*]}]}])
                         :where [?type :graphql.type/name]]
                       db)
                  (map first))]
    {::types      (->> base
                       (map (fn [m] (update m :graphql.type/fields #(->> %
                                                                         (map (fn [{:keys [graphql.field/name]
                                                                                    :as field}]
                                                                                [name field]))
                                                                         (into {})))))
                       (map (fn [{:keys [graphql.type/name]
                                  :as type}]
                              [name type]))
                       (into {}))
     ::attributes (->> base
                       (map (fn [{:keys [graphql.type/name
                                         graphql.type/fields]}]
                              [name
                               (reduce
                                (fn [m {:keys [graphql.field/attribute]
                                        :as field}]
                                  (-> m
                                      (assoc (:db/ident attribute) attribute)
                                      (update-in
                                       [(:db/ident attribute) :graphql.field/_attribute]
                                       conj
                                       (dissoc field :graphql.field/attribute))))
                                {}
                                fields)]))
                       (into {}))}))

(comment
  (get-schema (u/temp-db)))

(defn get-entity-types [db]
  (sort (keys (::types (get-schema db)))))

(comment
  (get-entity-types (u/temp-db)))

(defn get-collection [schema entity-type]
  (get-in schema [::types entity-type :graphql.type/collection :db/id]))

(comment
  (get-collection (get-schema (u/temp-db)) u/test-type-planetary-boundary))

(defn get-default-paths [schema entity-type]
  (->> (vals (get-in schema [::types entity-type :graphql.type/fields]))
       (map (fn [{:keys [graphql.field/name
                         graphql.field/target]}]
              (if target
                (str name "/id")
                name)))
       (concat ["id"])
       (sort)))

(comment
  (get-default-paths (get-schema (u/temp-db)) u/test-type-planetary-boundary))

(deftest get-default-paths-test
  (let [schema (get-schema (u/temp-db))
        paths  (get-default-paths schema u/test-type-planetary-boundary)]
    (is (= ["id" u/test-field-name (str u/test-field-quantifications "/id")]
           paths))))

(defn resolve-input-fields [schema input-obj entity-type]
  (let [temp-id    (get input-obj "id")
        uuid       (parse-uuid temp-id)
        input-obj  (dissoc input-obj "id")
        collection (get-collection schema entity-type)]
    (concat
     ; always add id
     [[:db/add collection :graphql.collection/entities temp-id]
      [:db/add temp-id :platform/id uuid]]
     ; generate adds for input-obj
     (for [[field value] input-obj
           :when (not (nil? value))
           :let [values       (if (sequential? value) value [value])
                 field-config (get-in schema [::types entity-type :graphql.type/fields field])
                 {:keys [graphql.field/attribute
                         graphql.field/target
                         graphql.field/backwards-ref?]} field-config
                 attr-ident   (:db/ident attribute)
                 target-type  (:graphql.type/name target)]
           :when (not (nil? attr-ident))
           value values]
       (if (and target-type (map? value))
         (if backwards-ref?
           [:db/add (get value "id") attr-ident temp-id]
           [:db/add temp-id attr-ident (get value "id")])
         [:db/add temp-id attr-ident value]))
     ; generate next level, if available
     (apply
      concat
      (for [[field value] input-obj
            :when (not (nil? value))
            :let [values       (if (sequential? value) value [value])
                  field-config (get-in schema [::types entity-type :graphql.type/fields field])
                  {:keys [graphql.field/target]} field-config
                  target-type  (:graphql.type/name target)]
            value values
            :when (and target-type (map? value))]
        (resolve-input-fields schema value target-type))))))

(deftest resolve-input-fields-test
  (let [conn (u/temp-conn)
        cid  (u/test-collection conn u/test-type-planetary-boundary)]
    (is (= [[:db/add cid :graphql.collection/entities "00000000-0000-0000-0000-000000000001"]
            [:db/add "00000000-0000-0000-0000-000000000001" :platform/id #uuid"00000000-0000-0000-0000-000000000001"]
            [:db/add "00000000-0000-0000-0000-000000000001" u/test-attribute-name "PlanetaryBoundary"]]
           (resolve-input-fields
            (get-schema (d/db conn))
            {"id"              "00000000-0000-0000-0000-000000000001"
             u/test-field-name "PlanetaryBoundary"}
            u/test-type-planetary-boundary))))

  (let [conn   (u/temp-conn)
        pb-cid (u/test-collection conn u/test-type-planetary-boundary)
        q-cid  (u/test-collection conn u/test-type-quantification)]
    (is (= [[:db/add pb-cid :graphql.collection/entities "00000000-0000-0000-0000-000000000001"]
            [:db/add "00000000-0000-0000-0000-000000000001" :platform/id #uuid"00000000-0000-0000-0000-000000000001"]
            [:db/add "00000000-0000-0000-0000-000000000001" u/test-attribute-name "PlanetaryBoundary"]
            [:db/add "00000000-0000-0000-0000-000000000001" u/test-attribute-quantifications "00000000-0000-0000-0000-000000000002"]
            [:db/add "00000000-0000-0000-0000-000000000001" u/test-attribute-quantifications "00000000-0000-0000-0000-000000000003"]
            [:db/add q-cid :graphql.collection/entities "00000000-0000-0000-0000-000000000002"]
            [:db/add "00000000-0000-0000-0000-000000000002" :platform/id #uuid"00000000-0000-0000-0000-000000000002"]
            [:db/add "00000000-0000-0000-0000-000000000002" u/test-attribute-name "Quantification"]
            [:db/add q-cid :graphql.collection/entities "00000000-0000-0000-0000-000000000003"]
            [:db/add "00000000-0000-0000-0000-000000000003" :platform/id #uuid"00000000-0000-0000-0000-000000000003"]
            [:db/add "00000000-0000-0000-0000-000000000003" u/test-attribute-name "Quantification2"]]
           (resolve-input-fields
            (get-schema (d/db conn))
            {"id"                         "00000000-0000-0000-0000-000000000001"
             u/test-field-name            "PlanetaryBoundary"
             u/test-field-quantifications [{"id"              "00000000-0000-0000-0000-000000000002"
                                            u/test-field-name "Quantification"}
                                           {"id"              "00000000-0000-0000-0000-000000000003"
                                            u/test-field-name "Quantification2"}]}
            u/test-type-planetary-boundary)))))

(defn gen-pull-pattern [schema entity-type fields]
  ; TODO nested selections
  (-> (->> (disj fields "id")
           (map (fn [field] (get-in schema [::types entity-type :graphql.type/fields field :graphql.field/attribute :db/ident])))
           (distinct))
      (conj :platform/id)))

(deftest gen-pull-pattern-test
  (let [schema  (get-schema (u/temp-db))
        pattern (gen-pull-pattern schema u/test-type-planetary-boundary #{"id" u/test-field-name})]
    (is (= [:platform/id u/test-attribute-name]
           pattern))))

(defn reverse-pull-pattern [schema entity-type fields-set pulled-entities]
  ; TODO reverse nested values
  (map
   (fn [entity]
     (-> (->> entity
              (mapcat
               (fn [[attribute datomic-value]]
                 (->> (get-in schema [::attributes entity-type attribute :graphql.field/_attribute])
                      (filter (fn [{:keys [graphql.field/name]}]
                                (contains? fields-set name)))
                      (map (fn [{:keys [graphql.field/name]}]
                             (let [value-type  (get-in schema [::attributes entity-type attribute :db/valueType :db/ident])
                                   cardinality (get-in schema [::attributes entity-type attribute :db/cardinality :db/ident])]
                               ; TODO reverse backref attributes
                               [name
                                (sa/->gql-value
                                 datomic-value
                                 value-type
                                 cardinality)]))))))
              (into {}))
         (assoc "id" (str (:platform/id entity)))))
   pulled-entities))

(deftest reverse-pull-pattern-test
  (is (= [{"id"   "00000000-0000-0000-0000-000000000000"
           "name" "x"}]
         (reverse-pull-pattern
          (get-schema (u/temp-db))
          u/test-type-planetary-boundary
          #{u/test-field-name}
          [{:platform/name "x"
            :platform/id #uuid "00000000-0000-0000-0000-000000000000"}]))))

(defn pull-and-resolve-entity-value [schema entity-uuid db entity-type selected-paths]
  ; TODO nested fields
  ; TODO continue using entity-uuid
  (let [fields  (set (filter #(not (str/includes? % "/")) selected-paths))
        pattern (gen-pull-pattern schema entity-type fields)]
    (->> [entity-uuid]
         (queries/pull-platform-entities db pattern)
         (reverse-pull-pattern schema entity-type fields)
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
