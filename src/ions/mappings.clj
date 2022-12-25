(ns ions.mappings)

(defn as-vec [x]
  (if (sequential? x)
    x
    (vector x)))

(defn map-entity [entity schema]
  {"id"         (str (get entity :db/id))
   "attributes" (->> (dissoc entity :db/id)
                     (map (fn [[k v]]
                            {"id"     (str (get-in schema [k :db/id]))
                             "name"   (str k)
                             "type"   (name (get-in schema [k :db/valueType]))
                             "values" (->> (as-vec v)
                                           (map str))})))})