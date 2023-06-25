(ns utils.cloud-import
  (:require [clojure.java.io :as io]
            [datomic.access :as access]
            [datomic.client.api :as d]
            [datomic.dev-local :as dl]
            [io.pedestal.log :as log])
  (:import (java.nio.channels OverlappingFileLockException)))

(comment
  ; doesn't seem to work :(
  (dl/divert-system {:system "climate-platform"})

  ; only errors when using divert-system...
  (d/connect (d/client (access/load-config)) {:db-name access/dev-env-db-name}))

; this works though, and could be filtered if it is to intensive
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

; https://docs.datomic.com/cloud/dev-local.html#import-cloud
(def import-cloud dl/import-cloud)

(defonce
  local-conn
  (do
    (try
      (import-cloud import-config)
      (catch OverlappingFileLockException e
        (throw e))
      (catch RuntimeException e
        (log/info :message "Import failed, trying again with clean storage dir"
                  :error (str e))
        (run! io/delete-file (reverse (file-seq local-storage-dir)))
        (import-cloud import-config))
      (catch Exception e
        (throw e)))
    (d/connect (d/client destination-client-config)
               {:db-name access/dev-env-db-name})))

(comment
  (d/db local-conn))
