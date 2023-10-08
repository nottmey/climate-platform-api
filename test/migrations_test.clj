(ns migrations-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [datomic.access :as access]
            [datomic.client.api :as d]
            [datomic.temp :as temp]
            [migrations :as m])
  (:import (clojure.lang ExceptionInfo)
           (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)))

(deftest migrations-each-have-unique-number-test
  (is
   (empty?
    (->> (-> (io/resource "migrations")
             (io/file)
             (file-seq))
         (map #(.getName %))
         (filter #(str/ends-with? % ".edn"))
         (map #(subs % 0 4))
         (frequencies)
         (filter (fn [[_ amount]] (> amount 1)))
         (map (fn [[prefix]]
                (throw (AssertionError. (str "Prefix '" prefix "' may not be used by multiple migrations!")))))))))

(declare thrown?)
(deftest dont-apply-if-migration-invalid
  (let [conn (temp/conn)]
    (d/transact conn {:tx-data [{:db/ident       :migration/id
                                 :db/valueType   :db.type/keyword
                                 :db/cardinality :db.cardinality/one
                                 :db/unique      :db.unique/value
                                 :db/doc         "Unique migration ID that identifies a transaction and indicates that the migration has already been performed."}]})

    (is (thrown? AssertionError (m/apply-migration! conn nil true)))
    (is (thrown? AssertionError (m/apply-migration! conn {} true)))
    (is (thrown? AssertionError (m/apply-migration! conn {:tx-data []} true)))
    (is (thrown? AssertionError (m/apply-migration! conn {:tx-id "123"} true)))
    (is (thrown? AssertionError (m/apply-migration! conn {:tx-id :123} true)))
    (is (thrown? AssertionError (m/apply-migration! conn {:tx-id   :123
                                                          :tx-data []} true)))
    (is (thrown? ExceptionInfo (m/apply-migration! conn {:tx-id   :123
                                                         :tx-data [:invalid-data]} true)))

    (is (some? (m/apply-migration! conn {:tx-id   :123
                                         :tx-data [{:db/doc "123"}]} true)))
    ; same id again should throw
    (is (thrown? ExceptionInfo (m/apply-migration! conn {:tx-id   :123
                                                         :tx-data [{:db/doc "123"}]} true)))))

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
      (m/-main))

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
      (m/apply-migrations-from-resources! conn true))))
