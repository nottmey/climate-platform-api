(ns datomic.queries
  (:require [datomic.client.api :as d]
            [user :as u]))

(defn get-attribute-index [db]
  (->> (d/pull db '{:eid      0
                    :selector [{:db.install/attribute [*]}]})
       :db.install/attribute
       (map #(update % :db/valueType :db/ident))
       (map #(update % :db/cardinality :db/ident))
       (map #(vector (:db/ident %) %))
       (into {})))

(comment
  (get-attribute-index (u/temp-db)))

(defn pull-entities [db pattern entities]
  (->> entities
       (map-indexed vector)
       (d/q '[:find ?idx (pull ?e pattern)
              :in $ pattern [[?idx ?e]]
              :where [?e]] db pattern)
       (sort-by first)
       (map second)))

(comment
  (pull-entities (u/temp-db) '[:db/doc] [0 1 2 3 4 5]))