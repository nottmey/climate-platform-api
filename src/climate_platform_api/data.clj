(ns climate-platform-api.data
  (:require [clojure.java.io :as io]
            [clojure.data.csv :as csv]))

(def initial-data [])

(comment
  (with-open [reader (io/reader "resources/data/climate-change.csv")]
    (let [csv-data (csv/read-csv reader)
          csv-maps (map zipmap
                        (repeat (first csv-data))
                        (rest csv-data))]
      (take 5 (doall 5 csv-maps)))))