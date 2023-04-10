(ns graphql.fields
  (:require
   [graphql.arguments :as arguments]
   [graphql.types :as types]
   [shared.attributes :as attributes]))

(def required-id arguments/required-id)

(def optional-session arguments/optional-session)

(def context
  {:name :context
   :type types/json-type})

(defn get-query [entity]
  {:name      (keyword (str "get" (name entity)))
   :arguments [arguments/required-id]
   :type      (name entity)})

(defn list-page-query
  ([entity] (list-page-query entity nil))
  ([entity filter-type]
   {:name           (keyword (str "list" (name entity)))
    :arguments      (concat
                     [{:name :page
                       :type types/page-query-type}]
                     (when filter-type
                       [{:name :filter
                         :type filter-type}]))
    :type           (types/list-page-type entity)
    :required-type? true}))

(defn publish-mutation [prefix entity]
  {:name      (str prefix (name entity))
   :arguments [arguments/required-id
               arguments/optional-session
               {:name           "value"
                :type           (types/input-type entity)
                :required-type? true}]
   :type      entity})

(defn subscription [prefix entity fields mutation-name]
  {:docstring "Reminder: A `null` argument will filter the result differently than omitting the argument entirely."
   :name      (str prefix (name entity))
   :arguments (concat
               [arguments/optional-id
                arguments/optional-session]
               (for [[field-name {:keys [graphql.relation/attribute]}] fields]
                 (let [{:keys [:graphql/type]}
                       (attributes/attribute->config attribute)]
                   {:name field-name
                    :type type})))
   :type      entity
   :directive (str "@aws_subscribe(mutations: [\"" mutation-name "\"])")})
