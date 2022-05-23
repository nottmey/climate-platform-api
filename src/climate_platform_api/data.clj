(ns climate-platform-api.data
  (:require [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [clojure.edn :as edn]
            [clojure.instant :as instant]))

(defn csv-lines->maps [csv-columns csv-data]
  (map zipmap
       (repeat csv-columns)
       (rest csv-data)))

(defn clean-values-fn [empty-vals]
  (let [empty-vals-set (set empty-vals)]
    (fn [m]
      (let [filled-ks (for [[k v] m
                            :when (not (contains? empty-vals-set v))]
                        k)]
        (select-keys m filled-ks)))))

(defn expand-columns-fn [{:keys [common-columns column-name value-name]}]
  (let [common-columns-set (set common-columns)]
    (fn [m]
      (let [common-column-data (select-keys m common-columns)]
        (for [[k v] m
              :when (not (contains? common-columns-set k))]
          (-> common-column-data
              (assoc column-name k)
              (assoc value-name v)))))))

(comment
  ((expand-columns-fn ["Source" "Year"])
   {"Source" "OWID", "Year" "2020", "Jan" "1", "Feb" "2", "Mar" "3"}))

(defn splitting-columns-fn [split-config]
  (fn [m]
    (reduce
      (fn [m [k split-fn]]
        (if-let [v (get m k)]
          (-> m
              (dissoc k)
              (assoc (split-fn v) v))
          m))
      m
      split-config)))

(comment
  ((splitting-columns-fn {:x #(if (odd? %) :odd :even)
                          :y #(if (pos-int? %) :positive :negative)})
   {:x 1 :y 2 :z 0}))

(defn coerce-values-fn [values-config]
  (let [xf (map (fn [[k v :as entry]]
                  (let [coerce-fn (values-config k)]
                    (if (and coerce-fn (not (nil? v)))
                      [k (coerce-fn v)]
                      entry))))]
    (fn [m] (into {} xf m))))

(comment
  ((coerce-values-fn {:date instant/read-instant-date})
   {:date "2005-07-16"}))

(defn load-csv-data [{:keys [csv/file csv/skip-lines csv/columns
                             steps/cleaning steps/expanding steps/splitting steps/coercing steps/adding]}]
  (with-open [reader (io/reader file)]
    (let [csv-maps (csv-lines->maps
                     columns
                     (drop skip-lines (csv/read-csv reader)))
          csv-xf   (comp
                     (map (clean-values-fn cleaning))
                     (mapcat (expand-columns-fn expanding))
                     (map (splitting-columns-fn splitting))
                     (map (coerce-values-fn coercing)))
          result   (sequence csv-xf csv-maps)]
      (doall result))))


(comment
  (load-csv-data
    {:csv/file        "resources/data/gistemp/l-oti/GLB.Ts+dSST.csv"
     :csv/skip-lines  2
     :csv/columns     [:time.slot/year
                       "Jan"
                       "Feb"
                       "Mar"
                       "Apr"
                       "May"
                       "Jun"
                       "Jul"
                       "Aug"
                       "Sep"
                       "Oct"
                       "Nov"
                       "Dec"
                       "J-D"
                       "D-N"
                       "DJF"
                       "MAM"
                       "JJA"
                       "SON"]
     :steps/cleaning  ["***"]
     :steps/expanding {:common-columns [:time.slot/year]
                       :column-name    :time.slot/month
                       :value-name     :temparature/difference}
     :steps/splitting {:time.slot/month #(if (contains? #{"J-D" "D-N" "DJF" "MAM" "JJA" "SON"} %)
                                           :time.slot/consecutive-months
                                           :time.slot/month)}
     :steps/coercing  {:time.slot/year               parse-long
                       :time.slot/month              #(condp = %
                                                        "Jan" 1
                                                        "Feb" 2
                                                        "Mar" 3
                                                        "Apr" 4
                                                        "May" 5
                                                        "Jun" 6
                                                        "Jul" 7
                                                        "Aug" 8
                                                        "Sep" 9
                                                        "Oct" 10
                                                        "Nov" 11
                                                        "Dec" 12)
                       :time.slot/consecutive-months #(condp = %
                                                        "J-D" "JJASON"
                                                        "D-N" "DJFMAM"
                                                        %)
                       :temparature/difference       parse-double}
     ; TODO implement prepending data and merging into individual values (instead of "steps/adding")
     ; TODO add "base-period" with value ":time.slot/consecutive-years" 1951-1980
     :steps/adding    {:value/type             :value.type/aggregation
                       :value.aggregation/type :value.aggregation.type/mean
                       :value/unit             :value.unit/celsius
                       :source/dataset         {:source.dataset/name "GLOBAL Land-Ocean Temperature Index"
                                                :source/direct       {:source/short-name  "GISTEMP v4"
                                                                      :source/long-name   "GISS Surface Temperature Analysis (GISTEMP), version 4"
                                                                      :source/description "The GISS Surface Temperature Analysis ver. 4 (GISTEMP v4) is an estimate of global surface temperature change. Graphs and tables are updated around the middle of every month using current data files from NOAA GHCN v4 (meteorological stations) and ERSST v5 (ocean areas), combined as described in our publications Hansen et al. (2010) and Lenssen et al. (2019)."
                                                                      :source/homepage    "https://data.giss.nasa.gov/gistemp/"}
                                                :source/underlying   [{:source/short-name "NOAA GHCN v4"}
                                                                      {:source/short-name "ERSST v5"}]}}}))

(def initial-data [])




