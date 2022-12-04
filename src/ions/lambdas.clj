(ns ions.lambdas
  (:require [clojure.pprint :as pp]
            [ions.utils :as utils]))

(defn- write-end-str ^String [x]
  (binding [*print-length* nil
            *print-level*  nil]
    (with-out-str (pp/pprint x))))

(defn get-schema [_]
  (-> (utils/get-db)
      utils/get-schema
      write-end-str))