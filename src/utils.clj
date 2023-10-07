(ns utils
  "general clojure utilities")

(defn remove-nil-vals [m]
  (->> (for [[k v] m
             :when (nil? v)]
         k)
       (apply dissoc m)))

(comment
  (remove-nil-vals {"something" nil
                    "x"         1}))
