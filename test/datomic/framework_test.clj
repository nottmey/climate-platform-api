(ns datomic.framework-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [datomic.client.api :as d]
            [datomic.temp :as temp]
            [testing :as t]
            [utils :as utils]
            [utils.cloud-import :refer [local-conn]]))

(defn cleanup-ident-refs [obj]
  (->> obj
       (map (fn [[k v :as kv]]
              (if (and (map? v) (keyword? (:db/ident v)))
                [k (:db/ident v)]
                kv)))
       (into {})))

(defn generate-attributes [conn]
  (->> (d/q '[:find (pull ?a [*])
              :where [_ :db.install/attribute ?a]]
            (d/db conn))
       (map first)
       (filter #(let [ident-ns (namespace (:db/ident %))]
                  (and
                   (not= ident-ns "db")
                   (not (str/starts-with? ident-ns "db."))
                   (not= ident-ns "fressian"))))
       (map #(dissoc % :db/id))
       (map #(cleanup-ident-refs %))
       (sort-by :db/ident)
       (vec)))

(comment
  (generate-attributes local-conn)

  (d/transact (temp/conn) {:tx-data (generate-attributes local-conn)})

  ; re-gen golden snapshot
  (pp/pprint (generate-attributes local-conn) (io/writer t/golden-attributes-file)))

(deftest attributes-golden-snapshot-test
  (let [golden-snapshot           (edn/read-string (slurp t/golden-attributes-file))
        generated-attributes-data (generate-attributes local-conn)]
    (is (= generated-attributes-data golden-snapshot))))

(defn generate-framework [conn]
  (->> (d/q '[:find (pull ?e [*
                              {:graphql.type/collection [*]}
                              {:graphql.type/fields [* {:graphql.field/target [:graphql.type/name]}]}])
              :where [?e :graphql.type/name]]
            (d/db conn))
       (map first)
       (map #(assoc % :db/id (str "txid-" (get % :graphql.type/name))))
       (map (fn [{type-name :graphql.type/name
                  :as       type}]
              (-> type
                  (update
                   :graphql.type/collection
                   (fn [coll]
                     (-> coll
                         (assoc :db/id (str "txid-" type-name "-collection"))
                         (dissoc :graphql.collection/entities))))
                  (update
                   :graphql.type/fields
                   (fn [fields]
                     (->> fields
                          (map
                           (fn [{field-name :graphql.field/name
                                 :as        field}]
                             (-> field
                                 (assoc :db/id (str "txid-" type-name "/" field-name))
                                 (update :graphql.field/target
                                         #(when % (str "txid-" (:graphql.type/name %))))
                                 (update :graphql.field/attribute
                                         #(:db/ident %))
                                 (utils/remove-nil-vals))))
                          (vec)))))))
       (sort-by :graphql.type/name)
       (vec)))

(comment
  (generate-framework local-conn)

  (let [conn (temp/conn)]
    (d/transact conn {:tx-data (generate-attributes local-conn)})
    (d/transact conn {:tx-data (generate-framework local-conn)}))

  ; re-gen golden snapshot
  (pp/pprint (generate-framework local-conn) (io/writer t/golden-framework-file)))

(deftest framework-golden-snapshot-test
  (let [golden-snapshot          (edn/read-string (slurp t/golden-framework-file))
        generated-framework-data (generate-framework local-conn)]
    (is (= generated-framework-data golden-snapshot))))
