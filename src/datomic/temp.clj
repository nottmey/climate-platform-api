(ns datomic.temp
  (:require [datomic.client.api :as d]))

(defn conn []
  (let [db-name "testing"
        client  (d/client {:server-type :dev-local
                           :storage-dir :mem
                           :system      db-name})
        arg-map {:db-name db-name}]
    (d/delete-database client arg-map)
    (d/create-database client arg-map)
    (d/connect client arg-map)))