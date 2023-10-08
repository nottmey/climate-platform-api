(ns ions.logging
  (:require [datomic.ion.cast :as cast]
            [io.pedestal.log :as log]
            [utils :as utils]))

; see https://docs.datomic.com/cloud/ions/ions-monitoring.html

; action-name: e.g. IndexerBasis, ConfigWrite
(defn info [action-name payload]
  (if (utils/local-mode?)
    (log/info :message action-name :payload payload)
    (let [event-body (assoc payload :msg action-name)]
      (cast/event event-body))))
