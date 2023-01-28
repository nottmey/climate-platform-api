(ns shared.operations.list
  (:require
    [datomic.client.api :as d]
    [datomic.schema :as ds]
    [user :as u]
    [clojure.string :as s]
    [graphql.fields :as f]
    [graphql.objects :as obj]
    [graphql.types :as t]
    [ions.utils :as utils]
    [shared.operations.operation :as o]))

(def prefix "list")

(defn list-query []
  (reify o/Operation
    (o/get-graphql-parent-type [_] t/query-type)
    (o/gen-graphql-field [_ entity]
      (f/list-page-query entity))
    (o/gen-graphql-object-types [_ entity]
      [(obj/list-page entity)])
    (o/resolves-graphql-field? [_ field]
      (s/starts-with? field prefix))
    (o/resolve-field-data [_ conn {:keys [field-name selected-paths arguments]}]
      (let [gql-type   (s/replace field-name prefix "")
            gql-fields (set (filter #(not (s/includes? % "/")) selected-paths))
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
    (time (o/resolve-field-data (list-query) conn {:field-name     "listPlanetaryBoundary"
                                                   :selected-paths #{"name" "nameX"}})))

  [(o/get-graphql-parent-type (list-query))
   (:name (o/gen-graphql-field (list-query) "Entity"))])