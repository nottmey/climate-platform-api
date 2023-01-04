(ns shared.attributes
  (:require
    [graphql.types :as gt])
  (:import (java.time.format DateTimeFormatter)
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
         :graphql/type                    gt/string-type
         :graphql/single-value-field-name :string
         :graphql/multi-value-field-name  :strings
         :datomic/type                    #{:db.type/symbol
                                            :db.type/string
                                            :db.type/keyword}
         :datomic/->gql                   str}
        {:graphql/name                    :Boolean
         :graphql/type                    gt/boolean-type
         :graphql/single-value-field-name :boolean
         :graphql/multi-value-field-name  :booleans
         :datomic/type                    #{:db.type/boolean}
         :datomic/->gql                   identity}
        {:graphql/name                    :Reference
         :graphql/type                    gt/id-type
         :graphql/single-value-field-name :ref
         :graphql/multi-value-field-name  :refs
         :datomic/type                    #{:db.type/ref}
         :datomic/->gql                   (fn [ref] (str (:db/id ref)))}
        {:graphql/name                    :DateTime
         :graphql/type                    gt/date-time-type
         :graphql/single-value-field-name :dateTime
         :graphql/multi-value-field-name  :dateTimes
         :datomic/type                    #{:db.type/instant}
         :datomic/->gql                   (fn [^Date date]
                                            (.format
                                              DateTimeFormatter/ISO_INSTANT
                                              (.toInstant date)))}]
       (map #(assoc % :graphql/single-value-type-name
                      (keyword (str (name (:graphql/name %))
                                    (name gt/attribute-type)))))
       (map #(assoc % :graphql/multi-value-type-name
                      (keyword (str "Multi"
                                    (name (:graphql/name %))
                                    (name gt/attribute-type)))))))

(comment
  (doall attribute-types)
  ((:datomic/->gql (first (drop 3 attribute-types)))
   (Date.)))