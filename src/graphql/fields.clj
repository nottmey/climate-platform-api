(ns graphql.fields
  (:require
   [graphql.arguments :as arguments]
   [graphql.types :as types]
   [shared.attributes :as attributes]))

(def required-id arguments/required-id)

(def context
  {:name :context
   :type types/json-type})

(defn get-query [field-name entity]
  {:name      field-name
   :arguments [arguments/required-id]
   :type      (name entity)})

(defn list-page-query
  ([field-name entity] (list-page-query field-name entity nil))
  ([field-name entity filter-type]
   {:name           field-name
    :arguments      (concat
                     [{:name :page
                       :type types/page-query-type}]
                     (when filter-type
                       [{:name :filter
                         :type filter-type}]))
    :type           (types/list-page-type entity)
    :required-type? true}))

(defn publish-mutation [field-name entity]
  {:name      field-name
   :arguments [arguments/required-id
               {:name           "value"
                :type           (types/input-type entity)
                :required-type? true}]
   :type      entity})

(defn subscription [field-name entity fields mutation-name]
  {:docstring "Reminder: A `null` argument will filter the result differently than omitting the argument entirely."
   :name      field-name
   :arguments (concat
               [arguments/optional-id]
               (for [[field-name {:keys [graphql.relation/attribute]}] fields]
                 (let [{:keys [:graphql/type]}
                       (attributes/attribute->config attribute)]
                   {:name field-name
                    :type type})))
   :type      entity
   :directive (str "@aws_subscribe(mutations: [\"" mutation-name "\"])")})
