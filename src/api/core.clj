(ns api.core
  (:require [api.data :as data]
            [api.schema :as schema]
            [com.walmartlabs.lacinia.pedestal2 :as lp]
            [com.walmartlabs.lacinia.schema :as ls]
            [datomic-lacinia.datomic :as dld]
            [datomic-lacinia.schema :as dls]
            [datomic.client.api :as d]
            [io.pedestal.http :as http]))

(defonce db-client (d/client {:server-type :dev-local
                              :storage-dir :mem
                              :system      "climate-platform"}))
(defonce db-args {:db-name "core"})
(defonce db-created? (d/create-database db-client db-args))
(defonce db-conn (d/connect db-client db-args))
(defonce db-attributes-tx (d/transact db-conn {:tx-data schema/initial-attributes}))
(defonce db-data-tx (d/transact db-conn {:tx-data data/initial-data}))

(defn compile-schema []
  (ls/compile
    (dls/gen-schema
      {:datomic/resolve-db #(d/db db-conn)
       :datomic/attributes (->> (dld/attributes (d/db db-conn))
                                (filter #(schema/valid-attribute-ident? (:db/ident %))))})))

; http://localhost:8888/ide
(defonce server nil)

(defn start! []
  (alter-var-root
    #'server
    (fn [prev-server]
      (when prev-server
        (http/stop prev-server))
      (-> compile-schema
          (lp/default-service nil)
          http/create-server
          http/start)))
  :started)

(defn stop! []
  (alter-var-root
    #'server
    (fn [prev-server]
      (when prev-server
        (http/stop prev-server))
      nil))
  :stopped)
