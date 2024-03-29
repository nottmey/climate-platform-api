(ns graphql.fields
  (:require
   [graphql.arguments :as arguments]
   [graphql.types :as types]
   [shared.mappings :as mappings]))

(def required-id arguments/required-id)

(defn get-query [field-name entity directives]
  {:name       field-name
   :arguments  [arguments/required-id]
   :type       (name entity)
   :directives directives})

(defn list-page-query
  ([field-name entity filter-type directives]
   {:name           field-name
    :arguments      (concat
                     [{:name :page
                       :type types/page-query-type}]
                     (when filter-type
                       [{:name :filter
                         :type filter-type}]))
    :type           (types/list-page-type entity)
    :required-type? true
    :directives     directives}))

(defn publish-mutation [field-name entity-name]
  {:name      field-name
   :arguments [(arguments/required-input-value entity-name)]
   :type      entity-name})

(defn subscription [field-name entity fields mutation-name]
  {:docstring  "Reminder: A `null` argument will filter the result differently than omitting the argument entirely."
   :name       field-name
   :arguments  (concat
                [arguments/optional-id]
                (for [[field-name {:keys [graphql.field/attribute]}] fields
                      :let [value-type (get-in attribute [:db/valueType :db/ident])]
                      :when (not= value-type :db.type/ref)]
                  {:name field-name
                   :type (mappings/value-type->field-type value-type)}))
   :type       entity
   :directives [{:name      :aws_subscribe
                 :arguments [{:name  :mutations
                              :value [mutation-name]}]}]})
