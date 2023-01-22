(ns cdk.app-sync
  (:require [datomic.access :as da]
            [datomic.client.api :as d]
            [datomic.schema :as ds]
            [graphql.schema :as schema]
            [ions.resolvers :as resolvers]
            [shared.operations :as ops]
            [shared.operations.operation :as o])
  (:import (software.amazon.awscdk Stack)
           (software.amazon.awscdk.services.appsync CfnApiKey$Builder CfnDataSource$Builder CfnDataSource$LambdaConfigProperty CfnGraphQLApi$Builder CfnGraphQLSchema$Builder CfnResolver$Builder)
           (software.amazon.awscdk.services.iam Effect PolicyStatement$Builder Role$Builder ServicePrincipal)))

(defn app-sync [^Stack stack]
  (let [db-name               da/dev-env-db-name
        conn                  (da/get-connection db-name)
        dynamic-entity-fields (ds/get-all-entity-fields (d/db conn))
        api                   (-> (CfnGraphQLApi$Builder/create stack "climate-platform-api")
                                  (.name "climate-platform-api")
                                  (.authenticationType "API_KEY")
                                  (.build))
        api-id                (-> api (.getAttrApiId))]
    (-> (CfnApiKey$Builder/create stack "climate-platform-api-key")
        (.apiId api-id)
        (.build))
    (let [api-schema                     (-> (CfnGraphQLSchema$Builder/create stack "climate-platform-api-schema")
                                             (.apiId api-id)
                                             (.definition (schema/generate))
                                             (.build))
          datomic-resolver-arn           "arn:aws:lambda:eu-central-1:118776085668:function:climate-platform-primary-datomic-resolver"
          datomic-resolver-access-role   (doto
                                           (-> (Role$Builder/create stack "datomic-resolver-access-role")
                                               (.assumedBy (ServicePrincipal. "appsync.amazonaws.com"))
                                               (.build))
                                           (.addToPolicy (-> (PolicyStatement$Builder/create)
                                                             (.effect Effect/ALLOW)
                                                             (.actions ["lambda:InvokeFunction"])
                                                             (.resources [datomic-resolver-arn])
                                                             (.build))))
          datomic-data-source            (-> (CfnDataSource$Builder/create stack "datomic-data-source")
                                             (.apiId api-id)
                                             (.name "DatomicDataSource")
                                             (.type "AWS_LAMBDA")
                                             (.lambdaConfig (-> (CfnDataSource$LambdaConfigProperty/builder)
                                                                (.lambdaFunctionArn datomic-resolver-arn)
                                                                (.build)))
                                             (.serviceRoleArn (.getRoleArn datomic-resolver-access-role))
                                             (.build))
          configure-datomic-resolver-for (fn [type-name field-name]
                                           (let [tn (name type-name)
                                                 fn (name field-name)]
                                             (-> (CfnResolver$Builder/create stack (str "datomic-resolver-" tn "-" fn))
                                                 (.apiId api-id)
                                                 (.typeName tn)
                                                 (.fieldName fn)
                                                 (.dataSourceName (.getName datomic-data-source))
                                                 (.build)
                                                 (doto (.addDependsOn api-schema)
                                                       (.addDependsOn datomic-data-source)))))]
      ;; https://docs.aws.amazon.com/appsync/latest/devguide/utility-helpers-in-util.html
      (doseq [[type-name field-name] @resolvers/resolvable-paths]
        (configure-datomic-resolver-for type-name field-name))
      (doseq [op (ops/all)
              [type-name] dynamic-entity-fields
              :let [field-name (:name (o/gen-graphql-field op type-name))]]
        (configure-datomic-resolver-for type-name field-name)))))