(ns datomic.access
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [datomic.client.api :as d]
            [datomic.utils :as u]
            [io.pedestal.log :as log]))

(def client-config-path "datomic/client-config.edn")

(def get-client
  (memoize
    #(if-let [r (io/resource client-config-path)]
       (do
         (log/info :message "loading client config")
         (d/client (edn/read-string (slurp r))))
       (throw (RuntimeException. (str "Missing " client-config-path))))))

(comment
  (time (get-client)))

(defn list-databases []
  (u/with-retry #(d/list-databases (get-client) {:timeout 1000 :limit -1})))

(comment
  (time (list-databases)))

(defn get-connection [db-name]
  (u/with-retry #(d/connect (get-client) {:timeout 1000 :db-name db-name})))

(comment
  (let [db-name (first (list-databases))]
    (time (get-connection db-name))))

(defn get-schema [db]
  (->> (d/pull db '{:eid 0 :selector [{:db.install/attribute [*]}]})
       :db.install/attribute
       (map #(update % :db/valueType :db/ident))
       (map #(update % :db/cardinality :db/ident))
       (map #(vector (:db/ident %) %))
       (into {})))

(comment
  (let [db-name (first (list-databases))
        conn (get-connection db-name)]
    (time (get-schema (d/db conn)))))