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

(defn generate []
  (let [db-name               da/dev-env-db-name
        conn                  (da/get-connection db-name)
        dynamic-type-fields   (ds/get-all-type-fields (d/db conn))
        dynamic-types         (-> (group-by first dynamic-type-fields)
                                  (update-vals #(map rest %)))
        entity-filter-type    (keyword (str (name t/entity-type) "Filter"))
        entity-list-type      (keyword (str (name t/entity-type) "List"))
        entity-list-page-type (keyword (str (name entity-list-type) "Page"))
        page-info-type        :PageInfo
        database-argument     {:name           :database
                               :type           t/id-type
                               :required-type? true}
        id-argument           {:name           :id
                               :type           t/id-type
                               :required-type? true}
        context-field         {:name :context
                               :type t/json-type}
        ; TODO add "to string" field, so that the client doesn't need to differentiate types when only displaying data
        attribute-fields      [{:name           :id
                                :type           t/id-type
                                :required-type? true}
                               {:name           :name
                                :type           t/string-type
                                :required-type? true}]]
    (str
      (gd/schema-definition
        {:root-ops {:query    t/query-type
                    :mutation t/mutation-type}})
      ; TODO generate from resolvers (via annotations on resolvers)
      (gd/input-object-type-definition
        {:name   entity-filter-type
         :fields [{:name           :attributes
                   :type           :ID
                   :list?          true
                   :required-type? true}]})
      (gd/object-type-definition
        {:name   t/query-type
         :fields [{:name           :databases
                   :type           t/id-type
                   :list?          true
                   :required-type? true
                   :required-list? true}
                  {:name      :get
                   :arguments [database-argument id-argument]
                   :type      t/entity-type}
                  {:name           :list
                   :arguments      [database-argument
                                    {:name :filter
                                     :type entity-filter-type}]
                   :type           entity-list-type
                   :required-type? true}]})
      ; example mutation, so it's not empty
      (gd/object-type-definition
        {:name   t/mutation-type
         :fields [{:name           :reset
                   :type           t/entity-type
                   :list?          true
                   :required-type? true
                   :required-list? true}]})
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
      (gd/interface-type-definition
        {:name   t/attribute-type
         :fields attribute-fields})
      (generate-attribute-subtypes attribute-fields)
      (gd/object-type-definition
        {:name   entity-list-type
         :fields [context-field
                  {:name           :total
                   :type           t/int-type
                   :required-type? true}
                  {:name           :page
                   :arguments      [{:name          :page
                                     ; FYI discarded by App Sync
                                     :default-value 0
                                     :type          t/int-type}
                                    {:name          :size
                                     ; FYI discarded by App Sync
                                     :default-value 20
                                     :type          t/int-type}]
                   :type           entity-list-page-type
                   :required-type? true}]})
      (gd/object-type-definition
        {:name   entity-list-page-type
         :fields [context-field
                  {:name           :info
                   :type           page-info-type
                   :required-type? true}
                  {:name           :entities
                   :type           t/entity-type
                   :list?          true
                   :required-type? true
                   :required-list? true}]})
      (gd/object-type-definition
        {:name   page-info-type
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
      (str/join
        (for [[type fields] dynamic-types]
          ; TODO generate queries for each type
          ; TODO add query resolvers for each type
          ; TODO generate mutations for each type
          ; TODO add mutation resolvers for each type
          (gd/object-type-definition
            {:name type
             :fields
             (conj
               (for [[field value-type] fields
                     :let [{:keys [graphql/type]}
                           (->> a/attribute-types
                                (filter
                                  (fn [{:keys [datomic/type]}]
                                    (contains? type value-type)))
                                first)]]
                 {:name field
                  :type type
                  ; TODO generate list?, if appropriate
                  ; TODO generate required?, if appropriate
                  })
               {:name "id"
                ; FYI not required, because of temp data (which may not have an ID yet)
                ; TODO how does app sync recommend handling ID generation when using subscriptions?
                :type :ID})}))))))

(comment
  (printf (generate))
  (spit (io/resource "cdk/schema.graphql") (generate)))