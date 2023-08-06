(ns datomic.queries
  (:require [clojure.test :refer [deftest is]]
            [datomic.access :as access]
            [datomic.client.api :as d]
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
  (->> (get-attribute-index (d/db (access/get-connection access/dev-env-db-name)))
       (sort-by first)
       (map last))

  (get-attribute-index (u/temp-db)))

(defn recently-updated-entities [db as]
  (->> (d/q '[:find ?e (max ?t)
              :in $ [?as ...]
              :where [?e ?as _ ?t]] db as)
       (sort-by second)
       (reverse)
       (map first)))

(defn pull-entities [db pattern entity-ids]
  (->> entity-ids
       (map-indexed vector)
       (d/q '[:find ?idx (pull ?e pattern)
              :in $ pattern [[?idx ?e]]
              :where [?e]] db pattern)
       (sort-by first)
       (map second)))

(comment
  (pull-entities (u/temp-db) '[:db/doc] [0 1 2 3 4 5]))

(defn pull-platform-entities [db pattern entity-uuids]
  (->> entity-uuids
       (map-indexed vector)
       (d/q '[:find ?idx (pull ?e pattern)
              :in $ pattern [[?idx ?id]]
              :where [?e :platform/id ?id]] db pattern)
       (sort-by first)
       (map second)))

(defn get-entities-sorted [db collection]
  (->> (d/q '[:find ?tx-instant ?id
              :in $ ?collection
              :where
              [?collection :graphql.collection/entities ?e]
              [?e :platform/id ?id ?tx]
              [?tx :db/txInstant ?tx-instant]]
            db
            collection)
       (sort)
       (map second)
       (distinct)))

(comment
  (let [db (d/db (access/get-connection access/dev-env-db-name))]
    (->> (d/q '[:find ?c :where [?c :graphql.collection/entities]] db)
         (map first)
         (map #(get-entities-sorted db %))
         (map #(pull-platform-entities db '[*] %)))))

(deftest get-entities-sorted-test
  (let [conn             (u/temp-conn)
        {:keys [tempids]} (d/transact conn {:tx-data [{:db/id  "tempid"
                                                       :db/doc "empty collection"}]})
        collection-id    (get tempids "tempid")
        generate-example (fn [uuid]
                           {:platform/id                  uuid
                            :graphql.collection/_entities collection-id})
        transact-example (fn [uuids]
                           (d/transact conn {:tx-data (map generate-example uuids)}))]
    (transact-example [#uuid "00000000-0000-0000-0000-000000000002"
                       #uuid "00000000-0000-0000-0000-000000000001"])
    (Thread/sleep 1)
    (transact-example [#uuid "00000000-0000-0000-0000-000000000000"])
    (is (= [#uuid"00000000-0000-0000-0000-000000000001"
            #uuid"00000000-0000-0000-0000-000000000002"
            #uuid"00000000-0000-0000-0000-000000000000"]
           (get-entities-sorted (d/db conn) collection-id)))))
