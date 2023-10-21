(ns graphql.schema
  (:require
   [clojure.string :as s]
   [datomic.client.api :as d]
   [datomic.framework :as framework]
   [graphql.directives :as directives]
   [graphql.fields :as fields]
   [graphql.objects :as objects]
   [graphql.spec :as spec]
   [graphql.types :as types]
   [shared.mappings :as mappings]
   [shared.operations :as ops]
   [testing :as t]))

(defn generate-attribute-subtypes [attribute-fields]
  (str
   (->> mappings/all-mappings
        (map
         #(spec/object-type-definition
           {:name       (:graphql/single-value-type-name %)
            :interfaces [types/attribute-type]
            :directives [directives/admin-access]
            :fields     (conj
                         attribute-fields
                         {:name           (:graphql/single-value-field-name %)
                          :type           (:graphql/type %)
                          :required-type? true})}))
        s/join)
   (->> mappings/all-mappings
        (map
         #(spec/object-type-definition
           {:name       (:graphql/multi-value-type-name %)
            :interfaces [types/attribute-type]
            :directives [directives/admin-access]
            :fields     (conj
                         attribute-fields
                         {:name           (:graphql/multi-value-field-name %)
                          :type           (:graphql/type %)
                          :list?          true
                          :required-type? true
                          :required-list? true})}))
        s/join)))

(comment
  (generate-attribute-subtypes [fields/required-id
                                {:name           :name
                                 :type           types/string-type
                                 :required-type? true}]))

(defn gen-entity-fields [fields input-type?]
  (concat
   [fields/required-id]
   (for [{:keys [graphql.field/name
                 graphql.field/attribute
                 graphql.field/target
                 graphql.field/backwards-ref?]}
         (->> (vals fields)
              (sort-by :graphql.field/name))]
     (let [target-type      (get target :graphql.type/name)
           value-type       (get-in attribute [:db/valueType :db/ident])
           field-type       (mappings/value-type->field-type value-type)
           attr-cardinality (get-in attribute [:db/cardinality :db/ident])
           list?            (or (= attr-cardinality :db.cardinality/many)
                                (and (= attr-cardinality :db.cardinality/one)
                                     backwards-ref?))]
       (assert (some? field-type) (str "There has to be a GraphQL type configured for value type " value-type "."))
       {:name           name
        :type           (if target-type
                          (if input-type?
                            (types/input-type target-type)
                            target-type)
                          field-type)
        :list?          list?
        :required-type? list?}))))

(comment
  (let [schema (framework/get-schema (t/temp-db))]
    (for [[_ {:keys [graphql.type/fields]}] (::framework/types schema)]
      (gen-entity-fields fields true))))

(defn generate [conn]
  (let [dynamic-schema-types (sort-by first (::framework/types (framework/get-schema (d/db conn))))
        entity-filter-type   (keyword (str (name types/entity-type) "Filter"))
        attribute-fields     [fields/required-id
                              {:name           :name
                               :type           types/string-type
                               :required-type? true}]
        ; TODO make the default auth be IAM, so no-one can accidentally access too much
        ; TODO ^ this would need a new way to generate the introspection schema
        ; TODO make these @aws_api_key authorized (method for all guest calls)
        query-fields         (seq (for [op ops/all-operations
                                        :when (= (::ops/parent-type op) types/query-type)
                                        [entity-type {:keys [graphql.type/fields]}] dynamic-schema-types]
                                    (ops/gen-graphql-field op entity-type fields)))
        ; TODO make these @aws_cognito_user_pools(cognito_groups: ["Users"]) authorized
        mutation-fields      (seq (for [op ops/all-operations
                                        :when (= (::ops/parent-type op) types/mutation-type)
                                        [entity-type {:keys [graphql.type/fields]}] dynamic-schema-types]
                                    (ops/gen-graphql-field op entity-type fields)))
        ; TODO make ions call via IAM
        subscription-fields  (seq (for [op ops/all-operations
                                        :when (= (::ops/parent-type op) types/subscription-type)
                                        [entity-type {:keys [graphql.type/fields]}] dynamic-schema-types]
                                    (ops/gen-graphql-field op entity-type fields)))]
    (str
     ;; static (db independent) schema
     (spec/schema-definition
      {:root-ops (merge
                  {:query types/query-type}
                  (when mutation-fields
                    {:mutation types/mutation-type})
                  (when subscription-fields
                    {:subscription types/subscription-type}))})
     (spec/interface-type-definition
      {:name   types/attribute-type
       :fields attribute-fields})
     (generate-attribute-subtypes attribute-fields)
     (spec/object-type-definition
      {:name       types/page-info-type
       :directives [directives/user-access]
       :fields     [{:name           :size
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
      {:name       entity-filter-type
       :directives [directives/admin-access]
       :fields     [{:name           :attributes
                     :type           types/id-type
                     :list?          true
                     :required-type? true}]})
     ;; entity framework & dynamic schema: query results
     (spec/object-type-definition
      {:name           types/query-type
       :spaced-fields? true
       :fields         (concat
                        [(fields/get-query
                          (str "get" (name types/entity-type))
                          types/entity-type
                          [directives/admin-access])
                         (fields/list-page-query
                          (str "list" (name types/entity-type))
                          types/entity-type
                          entity-filter-type
                          [directives/admin-access])]
                        query-fields)})
     (spec/object-type-definition
      {:name       types/entity-type
       :directives [directives/admin-access]
       :fields     [fields/required-id
                    {:name           :attributes
                     :type           types/attribute-type
                     :list?          true
                     :required-type? true
                     :required-list? true}]})
     (spec/interface-type-definition
      {:name   types/entity-base-type
       :fields [fields/required-id]})
     (s/join
      (for [[entity-type {:keys [graphql.type/fields]}] dynamic-schema-types]
        ; always generate all dynamic entity types
        (spec/object-type-definition
         {:name       entity-type
          :interfaces [types/entity-base-type]
          :directives [directives/user-access]
          :fields     (gen-entity-fields fields false)})))
     (spec/object-type-definition
      (objects/list-page types/entity-type [directives/admin-access]))
     (s/join
      (for [op          ops/all-operations
            entity-type (keys dynamic-schema-types)
            object-type (ops/gen-graphql-object-types op entity-type)]
        (spec/object-type-definition object-type)))
     ;; entity framework & dynamic schema: mutation inputs & results
     (s/join
      (for [[entity-type {:keys [graphql.type/fields]}] dynamic-schema-types]
        ; always generate all dynamic entity input types
        (spec/input-object-type-definition
         {:name   (types/input-type entity-type)
          :fields (gen-entity-fields fields true)})))
     (when mutation-fields
       (spec/object-type-definition
        {:name           types/mutation-type
         :spaced-fields? true
         :fields         mutation-fields}))
     (when subscription-fields
       (spec/object-type-definition
        {:name           types/subscription-type
         :spaced-fields? true
         :fields         subscription-fields})))))
