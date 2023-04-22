(ns shared.attributes
  (:require
   [clojure.data.json :as json]
   [graphql.types :as types])
  (:import
   (java.time.format DateTimeFormatter)
   (java.util Date)))

; TODO also use definition for mapping values
; TODO also map these values:
;#:db.type/bigdec
;#:db.type/bigint
;#:db.type/double
;#:db.type/float
;#:db.type/long
;#:db.type/tuple
;#:db.type/uuid
;#:db.type/uri
;#:db.type/bytes

(def attribute-types
  (->> [{:graphql/name                    :String
         :graphql/type                    types/string-type
         :graphql/single-value-field-name :string
         :graphql/multi-value-field-name  :strings
         :datomic/type                    #{:db.type/symbol
                                            :db.type/string
                                            :db.type/keyword
                                            :db.type/uuid}
         :datomic/->gql                   str}
        {:graphql/name                    :Boolean
         :graphql/type                    types/boolean-type
         :graphql/single-value-field-name :boolean
         :graphql/multi-value-field-name  :booleans
         :datomic/type                    #{:db.type/boolean}
         :datomic/->gql                   identity}
        {:graphql/name                    :Reference
         :graphql/type                    types/id-type
         :graphql/single-value-field-name :ref
         :graphql/multi-value-field-name  :refs
         :datomic/type                    #{:db.type/ref}
         :datomic/->gql                   (fn [ref] (str (:db/id ref)))}
        {:graphql/name                    :DateTime
         :graphql/type                    types/date-time-type
         :graphql/single-value-field-name :dateTime
         :graphql/multi-value-field-name  :dateTimes
         :datomic/type                    #{:db.type/instant}
         :datomic/->gql                   (fn [^Date date]
                                            (.format
                                             DateTimeFormatter/ISO_INSTANT
                                             (.toInstant date)))}
        {:graphql/name                    :Tuple
         ; TODO generate precise type
         :graphql/type                    types/json-type
         :graphql/single-value-field-name :tuple
         :graphql/multi-value-field-name  :tuples
         :datomic/type                    #{:db.type/tuple}
         :datomic/->gql                   (fn [tuple]
                                            (json/write-str tuple))}]
       (map #(assoc % :graphql/single-value-type-name
                    (keyword (str (name (:graphql/name %))
                                  (name types/attribute-type)))))
       (map #(assoc % :graphql/multi-value-type-name
                    (keyword (str "Multi"
                                  (name (:graphql/name %))
                                  (name types/attribute-type)))))))

(comment
  (doall attribute-types)

  ((:datomic/->gql (first (drop 3 attribute-types)))
   (Date.)))

(defn attribute->config [{:keys [db/valueType]}]
  (->> attribute-types
       (filter
        (fn [{:keys [datomic/type]}]
          (contains? type (:db/ident valueType))))
       first))

(comment
  (let [attribute #:db{:ident       :platform/name,
                       :valueType   #:db{:ident :db.type/string},
                       :cardinality #:db{:ident :db.cardinality/one}}]
    (attribute->config attribute)))

(def ^:private datomic-type->gql-fn
  (->> attribute-types
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