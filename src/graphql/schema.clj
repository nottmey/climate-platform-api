(ns graphql.schema
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [datomic.client.api :as d]
   [datomic.schema :as ds]
   [graphql.definitions :as gd]
   [graphql.fields :as fields]
   [graphql.objects :as obj]
   [graphql.types :as types]
   [shared.attributes :as a]
   [shared.operations :as ops]
   [shared.operations.operation :as o]
   [user :as u]))

(defn generate-attribute-subtypes [attribute-fields]
  (str
   (->>
    a/attribute-types
    (map
     #(gd/object-type-definition
       {:name       (:graphql/single-value-type-name %)
        :interfaces [types/attribute-type]
        :fields     (conj
                     attribute-fields
                     {:name           (:graphql/single-value-field-name %)
                      :type           (:graphql/type %)
                      :required-type? true})}))
    str/join)
   (->>
    a/attribute-types
    (map
     #(gd/object-type-definition
       {:name       (:graphql/multi-value-type-name %)
        :interfaces [types/attribute-type]
        :fields     (conj
                     attribute-fields
                     {:name           (:graphql/multi-value-field-name %)
                      :type           (:graphql/type %)
                      :list?          true
                      :required-type? true
                      :required-list? true})}))
    str/join)))

(comment
  (generate-attribute-subtypes [{:name           :id
                                 :type           types/id-type
                                 :required-type? true}
                                {:name           :name
                                 :type           types/string-type
                                 :required-type? true}]))

(defn gen-entity-fields [fields]
  (for [{:keys [graphql.relation/field
                graphql.relation/attribute]} fields]
    (let [{:keys [db/valueType
                  db/cardinality]} attribute
          {:keys [graphql/type]} (->> a/attribute-types
                                      (filter
                                       (fn [{:keys [datomic/type]}]
                                         (contains? type (:db/ident valueType))))
                                      first)
          list? (= (:db/ident cardinality) :db.cardinality/many)]
      (assert (some? type) (str "There is a GraphQL type configured for value type " valueType "."))
      {:name           field
       :type           type
       :list?          list?
       :required-list? list?
       :required-type? list?})))

(defn generate [conn]
  (let [dynamic-schema-types (:types (ds/get-graphql-schema (d/db conn)))
        entity-filter-type   (keyword (str (name types/entity-type) "Filter"))
        attribute-fields     [{:name           :id
                               :type           types/id-type
                               :required-type? true}
                              {:name           :name
                               :type           types/string-type
                               :required-type? true}]
        all-ops              (ops/all)]
    (str
     ;; static (db independent) schema
     (gd/schema-definition
      ; TODO add subscription type
      ; TODO add subscription annotations
      ; TODO use "publish" mutation for sending out data to subscriptions with generated id (in full) and after successful transaction
      {:root-ops {:query    types/query-type
                  :mutation types/mutation-type}})
     (gd/interface-type-definition
      {:name   types/attribute-type
       :fields attribute-fields})
     (generate-attribute-subtypes attribute-fields)
     (gd/object-type-definition
      {:name   types/page-info-type
       :fields [{:name           :size
                 :type           types/int-type
                 :required-type? true}
                {:name           :first
                 :type           types/int-type
                 :required-type? true}
                {:name :prev
                 :type types/int-type}
                {:name           :current
                 :type           types/int-type
                 :required-type? true}
                {:name :next
                 :type types/int-type}
                {:name           :last
                 :type           types/int-type
                 :required-type? true}]})
     ; entity framework & dynamic schema: query inputs
     (gd/input-object-type-definition
      {:name   types/page-query-type
       :fields [{:name          :number
                 :type          types/int-type
                 :default-value 0}
                {:name          :size
                 :type          types/int-type
                 :default-value 20}]})
     ; TODO generate filters for dynamic types
     (gd/input-object-type-definition
      {:name   entity-filter-type
       :fields [{:name           :attributes
                 :type           types/id-type
                 :list?          true
                 :required-type? true}]})
     ;; entity framework & dynamic schema: query results
     (gd/object-type-definition
      {:name   types/query-type
       :fields (concat
                [(fields/get-query types/entity-type)
                 (fields/list-page-query types/entity-type entity-filter-type)]
                (for [op     all-ops
                      :when (= (o/get-graphql-parent-type op) types/query-type)
                      entity (keys dynamic-schema-types)]
                  (o/gen-graphql-field op entity)))})
     (gd/object-type-definition
      {:name   types/entity-type
       :fields [fields/context
                {:name           :id
                 :type           types/id-type
                 :required-type? true}
                {:name           :attributes
                 :type           types/attribute-type
                 :list?          true
                 :required-type? true
                 :required-list? true}]})
     (str/join
      (for [[entity fields] dynamic-schema-types]
        ; always generate all dynamic entity types
        (gd/object-type-definition
         {:name   entity
          :fields (concat
                   [fields/context
                    {:name           "id"
                     :type           :ID
                     :required-type? true}]
                   (gen-entity-fields (vals fields)))})))
     (gd/object-type-definition
      (obj/list-page types/entity-type))
     (str/join
      (for [op          all-ops
            entity      (keys dynamic-schema-types)
            object-type (o/gen-graphql-object-types op entity)]
        (gd/object-type-definition object-type)))
     ;; entity framework & dynamic schema: mutation inputs & results
     (str/join
      (for [[entity fields] dynamic-schema-types]
        ; always generate all dynamic entity input types
        (gd/input-object-type-definition
         {:name   (types/input-type entity)
          :fields (gen-entity-fields (vals fields))})))
     (gd/object-type-definition
      {:name   types/mutation-type
       :fields (concat
                (for [op     all-ops
                      :when (= (o/get-graphql-parent-type op) types/mutation-type)
                      entity (keys dynamic-schema-types)]
                  (o/gen-graphql-field op entity)))}))))

(comment
  (let [schema (str (generate (u/temp-conn)))]
    schema)
  (let [schema (str (generate (u/temp-conn)))]
    (printf schema))
  ; re-gen golden snapshot
  (let [schema (str (generate (u/temp-conn)))]
    (spit (io/resource "cdk/schema.graphql") schema)))

(deftest generate-schema-test
  (let [golden-snapshot  (slurp (io/resource "cdk/schema.graphql"))
        generated-schema (str (generate (u/temp-conn)))]
    (is (string? golden-snapshot))
    (is (= generated-schema golden-snapshot))))
