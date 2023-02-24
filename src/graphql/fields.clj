(ns graphql.fields
  (:require
   [graphql.arguments :as a]
   [graphql.types :as t]
   [shared.attributes :as attributes]
   [shared.operations.operation :as o]))

(def context
  {:name :context
   :type t/json-type})

(defn get-query [entity]
  {:name      (keyword (str "get" (name entity)))
   :arguments [a/required-id]
   :type      (name entity)})

(defn list-page-query
  ([entity] (list-page-query entity nil))
  ([entity filter-type]
   {:name           (keyword (str "list" (name entity)))
    :arguments      (concat
                     [{:name :page
                       :type t/page-query-type}]
                     (when filter-type
                       [{:name :filter
                         :type filter-type}]))
    :type           (t/list-page-type entity)
    :required-type? true}))

(defn publish-mutation [prefix entity]
  {:name           (str prefix (name entity))
   :arguments      [a/required-id
                    {:name           "value"
                     :type           (t/input-type entity)
                     :required-type? true}]
   :type           entity
   :required-type? true})

(defn subscription [prefix entity fields mutation-op]
  {:docstring      "Reminder: A `null` argument will filter the result differently than omitting the argument entirely."
   :name           (str prefix (name entity))
   :arguments      (concat
                    [a/optional-id]
                    (for [[field-name {:keys [graphql.relation/attribute]}] fields]
                      (let [{:keys [:graphql/type]}
                            (attributes/attribute->config attribute)]
                        {:name field-name
                         :type type})))
   :type           entity
   :required-type? true
   :directive      (str "@aws_subscribe(mutations: [\""
                        (:name (o/gen-graphql-field mutation-op entity {}))
                        "\"])")})

(comment
  (subscription
   "onCreate"
   "PlanetaryBoundary"
   {"name" #:graphql.relation{:field     "name",
                              :attribute #:db{:ident       :platform/name,
                                              :valueType   #:db{:ident :db.type/string},
                                              :cardinality #:db{:ident :db.cardinality/one}},
                              :type      #:graphql.type{:name "PlanetaryBoundary"}}}
   (reify o/Operation (o/gen-graphql-field [_ _ _] {:name "publishCreatedPlanetaryBoundary"}))))