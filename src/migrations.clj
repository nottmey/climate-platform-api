(ns migrations
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [datomic.access :as access]
            [datomic.client.api :as d]
            [io.pedestal.log :as log]
            [user :as u]))

(defn apply-migration! [conn {:keys [tx-id tx-data]} with-local-tx-fns?]
  (assert (some? tx-id) "migration requires :tx-id")
  (assert (keyword? tx-id) "migration requires :tx-id to be a keyword")
  (assert (some? tx-data) "migration requires :tx-data")
  (assert (not-empty tx-data) "migration requires :tx-data to not be empty")
  (let [resolved-tx-fns (if with-local-tx-fns?
                          (let [db (d/db conn)]
                            (->>
                             tx-data
                             (mapcat
                              (fn [assertion]
                                (if (and (list? assertion) (symbol? (first assertion)))
                                  (let [[fn-symbol & rest] assertion]
                                    (apply (resolve fn-symbol) (conj rest db)))
                                  [assertion])))
                             (vec)))
                          tx-data)
        ; simply transact including id, and if id is not unique, an error is thrown anyway
        arg-map         {:tx-data (conj resolved-tx-fns
                                        {:db/id        "datomic.tx"
                                         :migration/id tx-id})}
        tx-result       (d/transact conn arg-map)]
    (log/info :message (str "Applied migration " tx-id))
    tx-result))

(defn apply-migrations-from-resources! [conn with-local-tx-fns?]
  (let [db                        (d/db conn)
        used-migration-ids        (->> (d/q '[:find ?mid
                                              :where [_ :migration/id ?mid]] db)
                                       (map first)
                                       (set))
        migrations-folder-content (-> (io/resource "migrations")
                                      (io/file)
                                      (file-seq))
        edn-files                 (->> migrations-folder-content
                                       (filter #(.isFile %))
                                       (filter #(str/ends-with? (.getName %) ".edn")))]
    (doall
     (for [edn-file edn-files
           :let [file-name (.getName edn-file)
                 tx-id     (keyword (subs file-name 0 (str/last-index-of file-name ".")))
                 migration (-> (edn/read-string (slurp edn-file))
                               (assoc :tx-id tx-id))]
           :when (not (contains? used-migration-ids tx-id))]
       (apply-migration! conn migration with-local-tx-fns?)))))

; entry point to perform all pending migrations
(defn -main []
  (apply-migrations-from-resources! (access/get-connection access/dev-env-db-name) false))
