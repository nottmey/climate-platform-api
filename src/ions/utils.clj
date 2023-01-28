(ns ions.utils)

(defn page-info [{:keys [number size]} total]
  (let [size        (min 100 (max 1 (or size 20)))
        last        (if (pos? total)
                      (dec (int (Math/ceil (/ total size))))
                      0)
        page-number (min last (max 0 (or number 0)))]
    {"size"    size
     "offset"  (* page-number size)
     "first"   0
     "prev"    (if (pos? page-number) (dec page-number) nil)
     "current" page-number
     "next"    (if (= page-number last) nil (inc page-number))
     "last"    last}))
