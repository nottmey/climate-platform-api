(ns tests
  (:require
    [clojure.test :refer [*testing-vars*]]
    [clojure.pprint :as pp]
    [datomic.client.api :as d]
    [datomic.attributes :as da]))

(defn test-mode? []
  (boolean (seq *testing-vars*)))

(def rel-type "PlanetaryBoundary")
(def rel-field "name")
(def rel-attribute :platform/name)
(def rel-sample-value (str rel-attribute " sample value"))

(defn temp-conn
  ([] (temp-conn "testing"))
  ([db-name]
   (let [client  (d/client {:server-type :dev-local
                            :storage-dir :mem
                            :system      db-name})
         arg-map {:db-name db-name}]
     (d/delete-database client arg-map)
     (d/create-database client arg-map)
     (let [conn (d/connect client arg-map)]
       (d/transact conn {:tx-data da/graphql-attributes})
       (d/transact conn {:tx-data da/platform-attributes})
       (d/transact conn {:tx-data (da/add-value-field-tx-data rel-type rel-field rel-attribute)})
       conn))))

(comment
  (temp-conn))

(defn pprint [o]
  (pp/pprint o))
