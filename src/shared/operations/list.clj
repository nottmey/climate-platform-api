(ns shared.operations.list
  (:require
   [clojure.string :as s]
   [datomic.client.api :as d]
   [datomic.schema :as ds]
   [graphql.fields :as fields]
   [graphql.objects :as objects]
   [graphql.types :as types]
   [ions.utils :as utils]
   [shared.operations.operation :as o]
   [user :as u]))

(def prefix "list")

(defn query []
  (reify o/Operation
    (o/get-graphql-parent-type [_] types/query-type)
    (o/gen-graphql-field [_ entity]
      (fields/list-page-query entity))
    (o/gen-graphql-object-types [_ entity]
      [(objects/list-page entity)])
    (o/resolves-graphql-field? [_ field-name]
      (s/starts-with? (name field-name) prefix))
    (o/resolve-field-data [_ conn {:keys [field-name selected-paths arguments]}]
      (let [gql-type   (s/replace (name field-name) prefix "")
            gql-fields (->> selected-paths
                            (filter #(s/starts-with? % "values/"))
                            (map #(s/replace % #"^values/" ""))
                            (filter #(not (s/includes? % "/")))
                            set)
            {:keys [page]} arguments
            db         (d/db conn)
            entities   (ds/get-entities-sorted db gql-type)
            page-info  (utils/page-info page (count entities))
            schema     (ds/get-graphql-schema db)
            pattern    (ds/gen-pull-pattern gql-type gql-fields schema)
            entities   (->> entities
                            (drop (get page-info "offset"))
                            (take (get page-info "size"))
                            (ds/pull-entities db pattern)
                            (ds/reverse-pull-pattern gql-type gql-fields schema))]
        {"info"   page-info
         "values" entities}))))

(comment
  (let [conn (u/sandbox-conn)]
    (time (o/resolve-field-data (query) conn {:field-name     :listPlanetaryBoundary
                                              :arguments      {:page {:number 2
                                                                      :size   10}}
                                              :selected-paths #{"name"}})))

  [(o/get-graphql-parent-type (query))
   (:name (o/gen-graphql-field (query) "Entity"))])