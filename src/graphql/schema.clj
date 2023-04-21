(ns graphql.schema
  (:require
   [clojure.java.io :as io]
   [clojure.string :as s]
   [clojure.test :refer [deftest is]]
   [datomic.client.api :as d]
   [datomic.framework :as framework]
   [graphql.fields :as fields]
   [graphql.objects :as objects]
   [graphql.spec :as spec]
   [graphql.types :as types]
   [shared.attributes :as attributes]
   [shared.operations :as ops]
   [user :as u]))

(defn generate-attribute-subtypes [attribute-fields]
  (str
   (->>
    attributes/attribute-types
    (map
     #(spec/object-type-definition
       {:name       (:graphql/single-value-type-name %)
        :interfaces [types/attribute-type]
        :fields     (conj
                     attribute-fields
                     {:name           (:graphql/single-value-field-name %)
                      :type           (:graphql/type %)
                      :required-type? true})}))
    s/join)
   (->>
    attributes/attribute-types
    (map
     #(spec/object-type-definition
       {:name       (:graphql/multi-value-type-name %)
        :interfaces [types/attribute-type]
        :fields     (conj
                     attribute-fields
                     {:name           (:graphql/multi-value-field-name %)
                      :type           (:graphql/type %)
                      :list?          true
                      :required-type? true
                      :required-list? true})}))
    s/join)))

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
          {:keys [graphql/type]} (attributes/attribute->config attribute)
          list? (= (:db/ident cardinality) :db.cardinality/many)]
      (assert (some? type) (str "There is a GraphQL type configured for value type " valueType "."))
      {:name           field
       :type           type
       :list?          list?
       :required-list? list?
       :required-type? list?})))

(defn generate [conn]
  (let [dynamic-schema-types (:types (framework/get-schema (d/db conn)))
        entity-filter-type   (keyword (str (name types/entity-type) "Filter"))
        attribute-fields     [fields/required-id
                              {:name           :name
                               :type           types/string-type
                               :required-type? true}]
        all-ops              (ops/all ::ops/any)]
    (str
     ;; static (db independent) schema
     (spec/schema-definition
      {:root-ops {:query        types/query-type
                  :mutation     types/mutation-type
                  :subscription types/subscription-type}})
     (spec/interface-type-definition
      {:name   types/attribute-type
       :fields attribute-fields})
     (generate-attribute-subtypes attribute-fields)
     (spec/object-type-definition
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
     (spec/input-object-type-definition
      {:name   types/page-query-type
       :fields [{:name  :number
                 :type  types/int-type
                 :value 0}
                {:name  :size
                 :type  types/int-type
                 :value 20}]})
     ; TODO generate filters for dynamic types
     (spec/input-object-type-definition
      {:name   entity-filter-type
       :fields [{:name           :attributes
                 :type           types/id-type
                 :list?          true
                 :required-type? true}]})
     ;; entity framework & dynamic schema: query results
     (spec/object-type-definition
      {:name   types/query-type
       :fields (concat
                [(fields/get-query (str "get" (name types/entity-type)) types/entity-type)
                 (fields/list-page-query (str "list" (name types/entity-type)) types/entity-type entity-filter-type)]
                (for [op all-ops
                      :when (= (::ops/parent-type op) types/query-type)
                      [entity fields] dynamic-schema-types]
                  (ops/gen-graphql-field op entity fields)))})
     (spec/object-type-definition
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
     (spec/interface-type-definition
      {:name   types/entity-base-type
       :fields [fields/required-id
                fields/optional-session]})
     (s/join
      (for [[entity fields] dynamic-schema-types]
        ; always generate all dynamic entity types
        (spec/object-type-definition
         {:name       entity
          :interfaces [types/entity-base-type]
          :fields     (concat
                       [fields/context
                        fields/required-id
                        fields/optional-session]
                       (gen-entity-fields (vals fields)))})))
     (spec/object-type-definition
      (objects/list-page types/entity-type))
     (s/join
      (for [op          all-ops
            entity      (keys dynamic-schema-types)
            object-type (ops/gen-graphql-object-types op entity)]
        (spec/object-type-definition object-type)))
     ;; entity framework & dynamic schema: mutation inputs & results
     (s/join
      (for [[entity fields] dynamic-schema-types]
        ; always generate all dynamic entity input types
        (spec/input-object-type-definition
         {:name   (types/input-type entity)
          :fields (gen-entity-fields (vals fields))})))
     (spec/object-type-definition
      {:name   types/mutation-type
       :fields (for [op all-ops
                     :when (= (::ops/parent-type op) types/mutation-type)
                     [entity fields] dynamic-schema-types]
                 (ops/gen-graphql-field op entity fields))})
     (spec/object-type-definition
      {:name           types/subscription-type
       :spaced-fields? true
       :fields         (for [op all-ops
                             :when (= (::ops/parent-type op) types/subscription-type)
                             [entity fields] dynamic-schema-types]
                         (ops/gen-graphql-field op entity fields))}))))

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
