(ns testing
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [datomic.client.api :as d]
   [datomic.temp :as temp]))

(def golden-attributes-file (io/resource "goldens/attributes.edn"))
(def golden-framework-file (io/resource "goldens/framework.edn"))

(defn apply-golden-attributes-file [conn]
  (d/transact conn {:tx-data (edn/read-string (slurp golden-attributes-file))}))

(defn apply-golden-framework-file [conn]
  (d/transact conn {:tx-data (edn/read-string (slurp golden-framework-file))}))

(defn temp-conn []
  (let [conn (temp/conn)]
    (apply-golden-attributes-file conn)
    (apply-golden-framework-file conn)
    conn))

(comment
  (temp-conn))

(defn temp-db []
  (d/db (temp-conn)))

(comment
  (temp-db))

(def testing-conn
  "default conn to be used in testing,
   or to be redefined when a different scenario is needed"
  temp-conn)

(defn testing-publish
  "default publish callback used in testing,
   or to be redefined when a different scenario is needed"
  [& _]
  (throw (AssertionError. "no call to publish expected")))
