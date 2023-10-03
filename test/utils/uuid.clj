(ns utils.uuid
  (:import (java.util UUID)))

(defn generate-uuid []
  (str (UUID/randomUUID)))

(comment
  (generate-uuid))
