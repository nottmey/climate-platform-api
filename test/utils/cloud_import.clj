(ns utils.cloud-import
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [datomic.access :as access]
            [datomic.client.api :as d]
            [datomic.local :as dl]
            [io.pedestal.log :as log]
            [ions.lambdas :as lambdas]
            [migrations :as m]
            [user :as u])
  (:import (java.io StringWriter)
           (java.nio.channels OverlappingFileLockException)))

(comment
  ; doesn't seem to work :(
  (dl/divert-system {:system "climate-platform"})

  ; only errors when using divert-system...
  (d/connect (d/client (access/load-config)) {:db-name access/dev-env-db-name}))

; the approach below works though, and could be filtered if it is to intensive
; no other connection can be started, so it's best to do this once at process startup, or use unique names
; once new things are transacted to local, you need to remove the data before importing, or choose a new name

(defonce local-storage-dir (io/file "." ".datomic-local"))

(defonce source-client-config (access/load-config))

(defonce destination-client-config {:server-type :dev-local
                                    ; ":storage-dir :mem" is not supported by import
                                    :storage-dir (.getCanonicalPath local-storage-dir)
                                    :system      (get source-client-config :system)})

(defonce import-config {:source (-> source-client-config
                                    (assoc :db-name access/dev-env-db-name))
                        :dest   (-> destination-client-config
                                    (assoc :db-name access/dev-env-db-name))})

; https://docs.datomic.com/cloud/datomic-local.html#import-cloud
(defn import-cloud [config]
  (let [err-out (new StringWriter)]
    (binding [*err* err-out]
      ; uses *err* to communicate, but it's not an error
      (dl/import-cloud config)
      (let [import-message (str err-out)]
        (when (pos? (count import-message))
          (log/info :message import-message))))))

(defonce
  local-conn
  (do
    (try
      (log/info :message "Starting import of test data")
      (import-cloud import-config)
      (catch OverlappingFileLockException e
        (throw e))
      (catch RuntimeException e
        (log/info :message "Import failed, trying again with clean storage dir"
                  :error (str e))
        (run! io/delete-file (reverse (file-seq local-storage-dir)))
        (import-cloud import-config))
      (catch Exception e
        (throw e))
      (finally
        (log/info :message "Finished import of test data")))
    (let [conn (d/connect (d/client destination-client-config)
                          {:db-name access/dev-env-db-name})]
      (log/info :message "Started applying migrations for testing")
      (m/apply-migrations-from-resources! conn true)
      (log/info :message "Finished applying migrations for testing")
      conn)))

(comment
  (d/tx-range local-conn {})

  (d/pull (d/db local-conn) '[*] 0))

(defn call-resolver-with-local-conn
  ([parent-type-name field-name selection-list]
   (call-resolver-with-local-conn parent-type-name field-name selection-list {}))
  ([parent-type-name field-name selection-list arguments]
   (with-redefs [u/testing-conn        (fn [] local-conn)
                 u/testing-publish     (fn [& _ignored])
                 ; for repl usage, also providing local-conn
                 access/get-connection (fn [& _ignored] local-conn)]
     (lambdas/call-datomic-resolver
      parent-type-name
      field-name
      selection-list
      arguments))))

(comment
  (call-resolver-with-local-conn
   "Query"
   "getQuantification"
   ["id" "name" "dataPoints/id" "planetaryBoundaries/id"]
   {"id" "14ef1024-cbb8-408c-87d3-9c94fe73ea67"})

  (call-resolver-with-local-conn
   "Query"
   "listPlanetaryBoundary"
   ["info/current"
    "values/id"
    "values/name"
    "values/description"
    "values/quantifications/id"
    "values/quantifications/name"
    "values/quantifications/dataPoints/id"
    "values/quantifications/dataPoints/value"]
   {"page" {"size" 10}}))
