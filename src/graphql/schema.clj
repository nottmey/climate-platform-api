(ns graphql.schema
  (:require [graphql.definitions :as d]
            [clojure.java.io :as io]))

(defn generate []
  (let [query-type        :Query
        mutation-type     :Mutation
        entity-type       :Entity
        database-argument {:name :database :type :ID :required-type? true}
        id-argument       {:name :id :type :ID :required-type? true}
        limit-argument    {:name :limit :type :Int}
        offset-argument   {:name :offset :type :Int}]
    (str
      (d/schema-definition
        {:root-ops {:query    query-type
                    :mutation mutation-type}})
      (d/object-type-definition
        {:name   query-type
         :fields [{:name           :databases
                   :type           :ID
                   :list?          true
                   :required-type? true}
                  {:name      :get
                   :arguments [database-argument id-argument]
                   :type      entity-type}
                  {:name           :list
                   :arguments      [database-argument limit-argument offset-argument]
                   :type           entity-type
                   :list?          true
                   :required-type? true}]})
      "# example mutation, so it's not empty\n"
      (d/object-type-definition
        {:name   mutation-type
         :fields [{:name           :reset
                   :type           entity-type
                   :list?          true
                   :required-type? true}]})
      "# example entity, with fields to dump data into\n"
      (d/object-type-definition
        {:name   entity-type
         :fields [{:name :id :type :ID :required-type? true}
                  {:name :data :type :String}]}))))

(comment
  (printf (generate))
  (spit (io/resource "cdk/schema.graphql") (generate)))