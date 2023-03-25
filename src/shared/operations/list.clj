(ns shared.operations.list
  (:require
   [clojure.string :as s]
   [datomic.client.api :as d]
   [datomic.queries :as queries]
   [datomic.schema :as schema]
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
    (o/gen-graphql-field [_ entity _]
      (fields/list-page-query entity))
    (o/gen-graphql-object-types [_ entity]
      [(objects/list-page entity)])
    (o/resolves-graphql-field? [_ field-name]
      (s/starts-with? (name field-name) prefix))
    (o/get-resolver-location [_] :datomic)
    (o/resolve-field-data [_ {:keys [initial-db schema field-name selected-paths arguments]}]
      (let [gql-type   (s/replace (name field-name) prefix "")
            gql-fields (->> selected-paths
                            (filter #(s/starts-with? % "values/"))
                            (map #(s/replace % #"^values/" ""))
                            (filter #(not (s/includes? % "/")))
                            set)
            {:keys [page]} arguments
            entities   (schema/get-entities-sorted initial-db gql-type)
            page-info  (utils/page-info page (count entities))
            pattern    (schema/gen-pull-pattern schema gql-type gql-fields)
            entities   (->> entities
                            (drop (get page-info "offset"))
                            (take (get page-info "size"))
                            (queries/pull-entities initial-db pattern)
                            (schema/reverse-pull-pattern schema gql-type gql-fields))]
        {:response {"info"   page-info
                    "values" entities}}))))

(comment
  (let [conn (u/temp-conn)]
    (time (o/resolve-field-data
           (query)
           {:conn           conn
            :initial-db     (d/db conn)
            :schema         (schema/get-schema (d/db conn))
            :field-name     :listPlanetaryBoundary
            :arguments      {:page {:number 2
                                    :size   10}}
            :selected-paths #{"name"}}))))
