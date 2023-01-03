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
  (->> [{:graphql/name :String
         :graphql/type :String
         :datomic/type #{:db.type/symbol
                         :db.type/string
                         :db.type/keyword}
         :db->gql      str}
        {:graphql/name :Boolean
         :graphql/type :Boolean
         :datomic/type #{:db.type/boolean}
         :db->gql      identity}
        {:graphql/name :Reference
         :graphql/type gt/entity-type
         :datomic/type #{:db.type/ref}
         :db->gql      (fn [ref] {:id (str (:db/id ref))})}
        {:graphql/name :DateTime
         :graphql/type gt/date-time-type
         :datomic/type #{:db.type/instant}
         :db->gql      (fn [^Date date]
                         (.format
                           DateTimeFormatter/ISO_INSTANT
                           (.toInstant date)))}]
       (map #(assoc % :graphql/single-value-full-name
                      (str (name (:graphql/name %))
                           (name gt/attribute-type))))
       (map #(assoc % :graphql/multi-value-full-name
                      (str "Multi"
                           (name (:graphql/name %))
                           (name gt/attribute-type))))))

(comment
  (doall attribute-types)
  ((:db->gql (first (drop 3 attribute-types)))
   (Date.)))