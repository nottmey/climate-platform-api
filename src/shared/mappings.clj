(ns shared.mappings
  (:require
   [clojure.data.json :as json]
   [datomic.client.api :as d]
   [datomic.queries :as queries]
   [graphql.types :as types]
   [user :as u])
  (:import
   (java.time.format DateTimeFormatter)
   (java.util Date)))

; value type = datomic
; field type = graphql

; TODO also use definition for mapping values
; TODO also map these values:
;#:db.type/bigdec
;#:db.type/bigint
;#:db.type/double
;#:db.type/float
;#:db.type/long
;#:db.type/tuple
;#:db.type/uri
;#:db.type/bytes

(defn- single-value-type [attribute-subtype]
  (keyword (str attribute-subtype (name types/attribute-type))))

(defn- multi-value-type [attribute-subtype]
  (keyword (str "Multi" attribute-subtype (name types/attribute-type))))

(def string-mapping
  {:graphql/type                    types/string-type
   :graphql/single-value-field-name :string
   :graphql/multi-value-field-name  :strings
   :graphql/single-value-type-name  (single-value-type "String")
   :graphql/multi-value-type-name   (multi-value-type "String")
   :datomic/type                    #{:db.type/symbol
                                      :db.type/string
                                      :db.type/keyword
                                      :db.type/uuid}
   :datomic/->gql                   str})

(def boolean-mapping
  {:graphql/type                    types/boolean-type
   :graphql/single-value-field-name :boolean
   :graphql/multi-value-field-name  :booleans
   :graphql/single-value-type-name  (single-value-type "Boolean")
   :graphql/multi-value-type-name   (multi-value-type "Boolean")
   :datomic/type                    #{:db.type/boolean}
   :datomic/->gql                   identity})

(def ref-mapping
  {:graphql/type                    types/id-type
   :graphql/single-value-field-name :ref
   :graphql/multi-value-field-name  :refs
   :graphql/single-value-type-name  (single-value-type "Reference")
   :graphql/multi-value-type-name   (multi-value-type "Reference")
   :datomic/type                    #{:db.type/ref}
   :datomic/->gql                   (fn [ref] (str (:db/id ref)))})

(def datetime-mapping
  {:graphql/type                    types/date-time-type
   :graphql/single-value-field-name :dateTime
   :graphql/multi-value-field-name  :dateTimes
   :graphql/single-value-type-name  (single-value-type "DateTime")
   :graphql/multi-value-type-name   (multi-value-type "DateTime")
   :datomic/type                    #{:db.type/instant}
   :datomic/->gql                   (fn [^Date date]
                                      (.format
                                       DateTimeFormatter/ISO_INSTANT
                                       (.toInstant date)))})

(def tuple-mapping
  {:graphql/type                    types/json-type         ; TODO generate precise type
   :graphql/single-value-field-name :tuple
   :graphql/multi-value-field-name  :tuples
   :graphql/single-value-type-name  (single-value-type "Tuple")
   :graphql/multi-value-type-name   (multi-value-type "Tuple")
   :datomic/type                    #{:db.type/tuple}
   :datomic/->gql                   (fn [tuple]
                                      (json/write-str tuple))})

(def all-mappings
  [string-mapping
   boolean-mapping
   ref-mapping
   datetime-mapping
   tuple-mapping])

(def supported-value-types
  (->> all-mappings
       (mapcat :datomic/type)
       set))

(comment
  (doall supported-value-types))

(def value-type->mapping
  (->> all-mappings
       (mapcat (fn [mapping]
                 (->> (:datomic/type mapping)
                      (map (fn [value-type]
                             [value-type mapping])))))
       (into {})))

(comment
  (doall value-type->mapping))

(defn value-type->field-type [value-type]
  ; TODO add dynamic types
  (-> value-type
      value-type->mapping
      :graphql/type))

(def ^:private datomic-type->gql-fn
  (->> all-mappings
       (mapcat
        (fn [{:keys [datomic/type datomic/->gql]}]
          (map #(vector % ->gql) type)))
       (into {})))

(defn ->gql-value [datomic-value datomic-type datomic-cardinality]
  (let [->gql (get datomic-type->gql-fn datomic-type)]
    (if (= datomic-cardinality :db.cardinality/many)
      (do
        (assert (sequential? datomic-value)
                (str "Value for :db.cardinality/many attribute " datomic-type " must be sequential."))
        (map ->gql datomic-value))
      (->gql datomic-value))))

(comment
  (->gql-value :db/ident :db.type/keyword :db.cardinality/one))

(defn map-value [attribute db-value attribute-index]
  (let [{:keys [db/cardinality db/valueType]} (get attribute-index attribute)
        many?       (= cardinality :db.cardinality/many)
        type-config (->> all-mappings
                         (filter
                          (fn [{:keys [datomic/type]}]
                            (contains? type valueType)))
                         first)]
    (assert (some? type-config) (str "There is a GraphQL type configured for value type " valueType "."))
    (let [{:keys [graphql/multi-value-field-name
                  graphql/single-value-field-name
                  graphql/multi-value-type-name
                  graphql/single-value-type-name
                  datomic/->gql]} type-config
          gql-type-name (if many? multi-value-type-name single-value-type-name)
          gql-key       (if many? multi-value-field-name single-value-field-name)
          gql-value     (if many? (map ->gql db-value) (->gql db-value))]
      {"__typename"   (name gql-type-name)
       (name gql-key) gql-value})))

(comment
  (let [db              (d/db (u/temp-conn))
        attribute-index (queries/get-attribute-index db)]
    #_(map-value :db/ident :db.part/db attribute-index)
    #_(map-value :db.install/partition [#:db{:id    0
                                             :ident :db.part/db}
                                        #:db{:id    3
                                             :ident :db.part/tx}
                                        #:db{:id    4
                                             :ident :db.part/user}] attribute-index)
    #_(map-value :db/doc "some docs" attribute-index)
    (map-value :graphql.relation/type+field [87960930222163 "name"] attribute-index)))

(defn map-entity [entity attribute-index]
  {"id"         (str (get entity :db/id))
   "attributes" (->> (dissoc entity :db/id)
                     (map
                      (fn [[a-key a-val]]
                        (merge
                         {"id"   (str (get-in attribute-index [a-key :db/id]))
                          "name" (str a-key)}
                         (map-value a-key a-val attribute-index)))))})

(comment
  (let [db              (d/db (u/temp-conn))
        attribute-index (queries/get-attribute-index db)
        result          (d/pull db '[*] 0)]
    (map-entity result attribute-index)))