(ns ions.lambdas
  (:require [clojure.pprint :as pp]
            [ions.utils :as utils]
            [clojure.data.json :as json]))

(defn- write-edn-str ^String [x]
  (binding [*print-length* nil
            *print-level*  nil]
    (with-out-str (pp/pprint x))))

(defn get-schema [_]
  (-> (utils/get-db)
      utils/get-schema
      write-edn-str))

(comment
  (get-schema nil))

(defn hello-world [params]
  (json/write-str {"message" (write-edn-str params)}))

(comment
  (hello-world {:input "Hello" :context {:something "World"}}))
