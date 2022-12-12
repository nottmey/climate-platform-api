(ns ions.resolvers
  (:require [datomic.client.api :as d]
            [ions.utils :as utils]))

(defn- extract-type-field-tuple [{:keys [parent-type-name field-name]}]
  [parent-type-name field-name])

(defmulti datomic-resolve extract-type-field-tuple)

(defmethod datomic-resolve [:Query :databases] [_]
  (utils/list-databases))

(comment
  (datomic-resolve {:parent-type-name :Query :field-name :databases}))

(defmethod datomic-resolve [:Query :get] [{:keys [arguments]}]
  (let [{:keys [database id]} arguments
        db     (d/db (utils/get-connection database))
        result (d/pull db '[*] (parse-long (str id)))]
    (hash-map "id" (get result :db/id)
              "data" (str result))))

(comment
  (datomic-resolve {:parent-type-name :Query
                    :field-name       :get
                    :arguments        {:database (first (utils/list-databases))
                                       :id       "0"}}))

(defmethod datomic-resolve [:Query :list] [{:keys [arguments]}]
  (let [{:keys [database offset limit]} arguments
        db     (d/db (utils/get-connection database))
        offset (max 0 (or offset 0))
        limit  (min 100 (max 1 (or limit 100)))
        es     (->> (d/datoms db {:index :eavt})
                    (map :e)
                    (distinct)
                    (drop offset)
                    (take limit))]
    (->> (d/q {:query  '[:find (pull ?es [*]) :in $ [?es ...]]
               :args   [db es]
               :offset offset
               :limit  limit})
         (map first)
         (map #(hash-map "id" (get % :db/id)
                         "data" (str %))))))

(comment
  (datomic-resolve {:parent-type-name :Query
                    :field-name       :list
                    :arguments        {:database (first (utils/list-databases))
                                       :limit    100
                                       :offset   0}}))