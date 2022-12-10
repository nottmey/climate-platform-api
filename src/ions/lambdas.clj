(ns ions.lambdas
  (:require [clojure.pprint :as pp]
            [ions.utils :as utils]
            [clojure.data.json :as json]))

(defn- write-end-str ^String [x]
  (binding [*print-length* nil
            *print-level*  nil]
    (with-out-str (pp/pprint x))))

(defn get-schema [_]
  (-> (utils/get-db)
      utils/get-schema
      write-end-str))

(comment
  (get-schema nil))

(defn hello-world [_]
  (json/write-str {"message" "Hello World!"}))

(comment
  (hello-world nil))
