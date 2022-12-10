(ns ions.resolvers
  (:require [ions.utils :as utils]))

(defn- extract-type-field-tuple [{:keys [parent-type-name field-name]}]
  [parent-type-name field-name])

(defmulti datomic-resolve extract-type-field-tuple)
(defmethod datomic-resolve [:Query :databases] [_]
  (utils/list-databases))

