(ns migrations-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [datomic.access :as access]
            [datomic.client.api :as d]
            [datomic.temp :as temp]
            [migrations :as migrations])
  (:import (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)))

(defn custom-tx-fn [_db message] [{:db/doc message}])

(defn path-to-migration-id [path]
  (let [file-name (.toString (.getFileName path))]
    (keyword (subs file-name 0 (str/last-index-of file-name ".")))))

(deftest apply-migrations-with-and-without-local-tx-fns
  (let [empty-file-attrs    (make-array FileAttribute 0)
        migrations-dir      (Files/createTempDirectory "testing-migrations" empty-file-attrs)
        first-migration     (Files/createTempFile migrations-dir "0001-first-migration" ".edn" empty-file-attrs)
        first-migration-id  (path-to-migration-id first-migration)
        second-migration    (Files/createTempFile migrations-dir "0002-second-migration" ".edn" empty-file-attrs)
        second-migration-id (path-to-migration-id second-migration)
        conn                (temp/conn)]
    (.deleteOnExit (.toFile first-migration))
    (.deleteOnExit (.toFile second-migration))
    (.deleteOnExit (.toFile migrations-dir))
    (spit (.toFile first-migration)
          (prn-str {:tx-data [{:db/doc "Migration which was already applied"}]}))
    (spit (.toFile second-migration)
          (prn-str {:tx-data [{:db/doc "Migration which should be applied"}
                              '(migrations-test/custom-tx-fn "any call to an tx-fn")]}))
    (d/transact conn {:tx-data [{:db/ident       :migration/id
                                 :db/valueType   :db.type/keyword
                                 :db/cardinality :db.cardinality/one
                                 :db/unique      :db.unique/value
                                 :db/doc         "Unique migration ID that identifies a transaction and indicates that the migration has already been performed."}]})
    (d/transact conn {:tx-data [{:db/id        "datomic.tx"
                                 :migration/id first-migration-id}]})

    ; main call, without local tx-fns
    (with-redefs [access/get-connection (fn [_] conn)
                  io/resource           (fn [_] (.toURL (.toUri migrations-dir)))
                  d/transact            (fn [_ {:keys [tx-data]}]
                                          ; no first migration, just second
                                          (is (= tx-data
                                                 [{:db/doc "Migration which should be applied"}
                                                  '(migrations-test/custom-tx-fn "any call to an tx-fn")
                                                  {:db/id        "datomic.tx"
                                                   :migration/id second-migration-id}])))]
      (migrations/-main))

    ; direct call, with local tx-fns
    (with-redefs [access/get-connection (fn [_] conn)
                  io/resource           (fn [_] (.toURL (.toUri migrations-dir)))
                  d/transact            (fn [_ {:keys [tx-data]}]
                                          ; no first migration, just second
                                          (is (= tx-data
                                                 [{:db/doc "Migration which should be applied"}
                                                  {:db/doc "any call to an tx-fn"}
                                                  {:db/id        "datomic.tx"
                                                   :migration/id second-migration-id}])))]
      (migrations/apply-migrations-from-resources! conn true))))
