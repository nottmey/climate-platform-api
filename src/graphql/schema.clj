(ns graphql.schema
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [datomic.access :as da]
            [datomic.client.api :as d]
            [graphql.definitions :as gd]
            [graphql.types :as t]
            [shared.attributes :as a]
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

(def id-argument
  {:name           :id
   :type           t/id-type
   :required-type? true})

(def context-field
  {:name :context
   :type t/json-type})

(defn get-query [type]
  {:name      (keyword (str "get" (name type)))
   :arguments [id-argument]
   :type      (name type)})

(defn list-page-type [type]
  (keyword (str (name type) "ListPage")))

(defn list-page-query
  ([type] (list-page-query type nil))
  ([type filter-type]
   {:name           (keyword (str "list" (name type)))
    :arguments      (concat
                      (when filter-type
                        [{:name :filter
                          :type filter-type}])
                      [{:name          :page
                        ; FYI discarded by App Sync
                        :default-value 0
                        :type          t/int-type}
                       {:name          :size
                        ; FYI discarded by App Sync
                        :default-value 20
                        :type          t/int-type}])
    :type           (list-page-type type)
    :required-type? true}))

(defn list-page-definition [type]
  (gd/object-type-definition
    {:name   (list-page-type type)
     :fields [context-field
              {:name           :info
               :type           t/page-info-type
               :required-type? true}
              {:name           :values
               :type           type
               :list?          true
               :required-type? true
               :required-list? true}]}))

(defn generate []
  (let [db-name             da/dev-env-db-name
        conn                (da/get-connection db-name)
        dynamic-type-fields (ds/get-all-type-fields (d/db conn))
        dynamic-types       (-> (group-by first dynamic-type-fields)
                                (update-vals #(map rest %)))
        entity-filter-type  (keyword (str (name t/entity-type) "Filter"))
        attribute-fields    [{:name           :id
                              :type           t/id-type
                              :required-type? true}
                             {:name           :name
                              :type           t/string-type
                              :required-type? true}]]
    (str
      ; static (db independent) schema
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
      ; TODO generate filters for dynamic types
      (gd/input-object-type-definition
        {:name   entity-filter-type
         :fields [{:name           :attributes
                   :type           :ID
                   :list?          true
                   :required-type? true}]})
      ; entity framework & dynamic schema: query results
      (gd/object-type-definition
        {:name   t/query-type
         :fields (concat
                   ; TODO add query resolvers for each type (and fix entity resolvers)
                   [(get-query t/entity-type)
                    (list-page-query t/entity-type entity-filter-type)]
                   (for [[type] dynamic-types]
                     (get-query type))
                   (for [[type] dynamic-types]
                     (list-page-query type)))})
      (gd/object-type-definition
        {:name   t/entity-type
         :fields [context-field
                  {:name           :id
                   :type           t/id-type
                   :required-type? true}
                  {:name           :attributes
                   :type           t/attribute-type
                   :list?          true
                   :required-type? true
                   :required-list? true}]})
      (str/join
        (for [[type fields] dynamic-types]
          (gd/object-type-definition
            {:name type
             :fields
             (concat
               [context-field
                {:name           "id"
                 :type           :ID
                 :required-type? true}]
               (for [[field value-type cardinality] fields]
                 (let [{:keys [graphql/type]} (->> a/attribute-types
                                                   (filter
                                                     (fn [{:keys [datomic/type]}]
                                                       (contains? type value-type)))
                                                   first)
                       list? (= cardinality :db.cardinality/many)]
                   (assert (some? type) (str "There is a GraphQL type configured for value type " value-type "."))
                   {:name           field
                    :type           type
                    :list?          list?
                    :required-list? list?
                    :required-type? list?})))})))
      (list-page-definition t/entity-type)
      (str/join
        (for [[type] dynamic-types]
          (list-page-definition type)))
      ; entity framework & dynamic schema: mutation inputs & results
      (gd/object-type-definition
        {:name   t/mutation-type
         ; example mutation, so it's not empty
         ; TODO generate mutations for each type
         ; TODO add mutation resolvers for each type
         :fields [{:name           :reset
                   :type           t/entity-type
                   :list?          true
                   :required-type? true
                   :required-list? true}]}))))

(comment
  (printf (generate))
  (spit (io/resource "cdk/schema.graphql") (generate)))