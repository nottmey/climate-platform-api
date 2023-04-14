(ns datomic.access
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is]]
   [datomic.client.api :as d]
   [datomic.utils :as utils])
  (:import (clojure.lang ExceptionInfo)
           (datomic.client.api.protocols Connection)
           (java.net URI)))

(def client-config-path "datomic/client-config.edn")

(def dev-env-db-name "development")

(defn load-config []
  (if-let [r (io/resource client-config-path)]
    (edn/read-string (slurp r))
    (throw (RuntimeException. (str "Missing " client-config-path)))))

(defn- load-client []
  (d/client (load-config)))

(comment
  (d/list-databases (load-client) {}))

(declare thrown?)
(deftest load-client-test
  (with-redefs [client-config-path "datomic/missing-client-config.edn"]
    (is (thrown? RuntimeException (load-client))))
  (with-redefs [d/client (fn [{:keys [server-type region system endpoint]}]
                           (is (= :ion server-type))
                           (is (= "eu-central-1" region))
                           (is (not-empty system))
                           (is (uri? (URI/create endpoint))))]
    (load-client)))

(def get-client (memoize load-client))

(defn get-connection [db-name]
  (utils/with-retry #(d/connect (get-client) {:timeout 1000
                                              :db-name db-name})))

(comment
  (get-connection dev-env-db-name))

(deftest get-connection-test
  (let [db-name "something"]
    (with-redefs [get-client #(d/client {:server-type :dev-local
                                         :storage-dir :mem
                                         :system      db-name})]
      (d/create-database (get-client) {:db-name db-name})
      (is (thrown? ExceptionInfo (get-connection "hello")))
      (is (instance? Connection (get-connection db-name))))))