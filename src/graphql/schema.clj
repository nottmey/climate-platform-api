(ns graphql.schema
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [graphql.definitions :as d]
            [graphql.types :as t]
            [shared.attributes :as a]))

(defn generate-attribute-subtypes [attribute-fields]
  (str
    (->>
      a/attribute-types
      (map
        #(d/object-type-definition
           {:name       (:graphql/single-value-full-name %)
            :interfaces [t/attribute-type]
            :fields     (conj
                          attribute-fields
                          {:name           :value
                           :type           (:graphql/type %)
                           :required-type? true})}))
      str/join)
    (->>
      a/attribute-types
      (map
        #(d/object-type-definition
           {:name       (:graphql/multi-value-full-name %)
            :interfaces [t/attribute-type]
            :fields     (conj
                          attribute-fields
                          {:name           :values
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
  (let [entity-list-type      (keyword (str (name t/entity-type) "List"))
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
        attribute-fields      [{:name           :id
                                :type           t/id-type
                                :required-type? true}
                               {:name           :name
                                :type           t/string-type
                                :required-type? true}]]
    (str
      (d/schema-definition
        {:root-ops {:query    t/query-type
                    :mutation t/mutation-type}})
      ; TODO generate from resolvers (via annotations on resolvers)
      (d/object-type-definition
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
                   :arguments      [database-argument]
                   :type           entity-list-type
                   :required-type? true}]})
      ; example mutation, so it's not empty
      (d/object-type-definition
        {:name   t/mutation-type
         :fields [{:name           :reset
                   :type           t/entity-type
                   :list?          true
                   :required-type? true
                   :required-list? true}]})
      (d/object-type-definition
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
      (d/interface-type-definition
        {:name   t/attribute-type
         :fields attribute-fields})
      (generate-attribute-subtypes attribute-fields)
      (d/object-type-definition
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
      (d/object-type-definition
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
      (d/object-type-definition
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
                   :required-type? true}]}))))

(comment
  (printf (generate))
  (spit (io/resource "cdk/schema.graphql") (generate)))