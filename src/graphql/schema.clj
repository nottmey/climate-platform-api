(ns graphql.schema
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [datomic.access :as da]
    [datomic.client.api :as d]
    [graphql.definitions :as gd]
    [graphql.fields :as f]
    [graphql.types :as t]
    [graphql.objects :as obj]
    [shared.attributes :as a]
    [shared.operations :as ops]
    [shared.operations.operation :as o]
    [datomic.schema :as ds]))

(defn generate-attribute-subtypes [attribute-fields]
  (str
    (->>
      a/attribute-types
      (map
        #(gd/object-type-definition
           {:name       (:graphql/single-value-type-name %)
            :interfaces [t/attribute-type]
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
            :interfaces [t/attribute-type]
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
                                 :type           t/id-type
                                 :required-type? true}
                                {:name           :name
                                 :type           t/string-type
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

(defn generate []
  (let [db-name              da/dev-env-db-name
        conn                 (da/get-connection db-name)
        dynamic-schema-types (:types (ds/get-graphql-schema (d/db conn)))
        entity-filter-type   (keyword (str (name t/entity-type) "Filter"))
        attribute-fields     [{:name           :id
                               :type           t/id-type
                               :required-type? true}
                              {:name           :name
                               :type           t/string-type
                               :required-type? true}]
        all-ops              (ops/all)]
    (str
      ;; static (db independent) schema
      (gd/schema-definition
        ; TODO add subscription type
        ; TODO add subscription annotations
        ; TODO use "publish" mutation for sending out data to subscriptions with generated id (in full) and after successful transaction
        {:root-ops {:query    t/query-type
                    :mutation t/mutation-type}})
      (gd/interface-type-definition
        {:name   t/attribute-type
         :fields attribute-fields})
      (generate-attribute-subtypes attribute-fields)
      (gd/object-type-definition
        {:name   t/page-info-type
         :fields [{:name           :size
                   :type           t/int-type
                   :required-type? true}
                  {:name           :first
                   :type           t/int-type
                   :required-type? true}
                  {:name :prev
                   :type t/int-type}
                  {:name           :current
                   :type           t/int-type
                   :required-type? true}
                  {:name :next
                   :type t/int-type}
                  {:name           :last
                   :type           t/int-type
                   :required-type? true}]})
      ; entity framework & dynamic schema: query inputs
      (gd/input-object-type-definition
        {:name   t/page-query-type
         :fields [{:name          :number
                   :type          t/int-type
                   :default-value 0}
                  {:name          :size
                   :type          t/int-type
                   :default-value 20}]})
      ; TODO generate filters for dynamic types
      (gd/input-object-type-definition
        {:name   entity-filter-type
         :fields [{:name           :attributes
                   :type           t/id-type
                   :list?          true
                   :required-type? true}]})
      ;; entity framework & dynamic schema: query results
      (gd/object-type-definition
        {:name   t/query-type
         :fields (concat
                   [(f/get-query t/entity-type)
                    (f/list-page-query t/entity-type entity-filter-type)]
                   (for [op     all-ops
                         :when (= (o/get-graphql-parent-type op) t/query-type)
                         entity (keys dynamic-schema-types)]
                     (o/gen-graphql-field op entity)))})
      (gd/object-type-definition
        {:name   t/entity-type
         :fields [f/context
                  {:name           :id
                   :type           t/id-type
                   :required-type? true}
                  {:name           :attributes
                   :type           t/attribute-type
                   :list?          true
                   :required-type? true
                   :required-list? true}]})
      (str/join
        (for [[entity fields] dynamic-schema-types]
          ; always generate all dynamic entity types
          (gd/object-type-definition
            {:name   entity
             :fields (concat
                       [f/context
                        {:name           "id"
                         :type           :ID
                         :required-type? true}]
                       (gen-entity-fields (vals fields)))})))
      (gd/object-type-definition
        (obj/list-page t/entity-type))
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
            {:name   (t/input-type entity)
             :fields (gen-entity-fields (vals fields))})))
      (gd/object-type-definition
        {:name   t/mutation-type
         :fields (concat
                   (for [op     all-ops
                         :when (= (o/get-graphql-parent-type op) t/mutation-type)
                         entity (keys dynamic-schema-types)]
                     (o/gen-graphql-field op entity)))}))))

(comment
  (generate)
  (printf (generate))
  (spit (io/resource "cdk/schema.graphql") (generate)))