(ns cdk.app-sync
  (:require
   [clojure.java.io :as io]
   [datomic.access :as access]
   [datomic.client.api :as d]
   [datomic.framework :as framework]
   [graphql.schema :as schema]
   [ions.resolvers :as resolvers]
   [shared.operations :as ops])
  (:import
   (software.amazon.awscdk
    Stack)
   (software.amazon.awscdk.services.appsync
    CfnApiKey$Builder
    CfnDataSource$Builder
    CfnDataSource$LambdaConfigProperty
    CfnGraphQLApi$Builder
    CfnGraphQLSchema$Builder
    CfnResolver$AppSyncRuntimeProperty
    CfnResolver$Builder CfnResolver$PipelineConfigProperty)
   (software.amazon.awscdk.services.iam
    Effect
    PolicyStatement$Builder
    Role$Builder
    ServicePrincipal)))

; Docs: https://docs.aws.amazon.com/cdk/api/v2/docs/aws-construct-library.html
(defn app-sync [^Stack stack]
  (let [conn   (access/get-connection access/dev-env-db-name)
        api    (-> (CfnGraphQLApi$Builder/create stack "climate-platform-api")
                   (.name "climate-platform-api")
                   (.authenticationType "API_KEY")
                   (.build))
        api-id (-> api (.getAttrApiId))]
    (-> (CfnApiKey$Builder/create stack "climate-platform-api-key")
        (.apiId api-id)
        (.build))
    (let [api-schema                     (-> (CfnGraphQLSchema$Builder/create stack "climate-platform-api-schema")
                                             (.apiId api-id)
                                             (.definition (schema/generate conn))
                                             (.build))
          datomic-resolver-arn           "arn:aws:lambda:eu-central-1:118776085668:function:climate-platform-primary-datomic-resolver"
          datomic-resolver-access-role   (doto (-> (Role$Builder/create stack "datomic-resolver-access-role")
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
          configure-datomic-resolver     (fn [type-name field-name]
                                           (let [tn (name type-name)
                                                 fn (name field-name)]
                                             (-> (CfnResolver$Builder/create stack (str "datomic-resolver-" tn "-" fn))
                                                 (.apiId api-id)
                                                 (.typeName tn)
                                                 (.fieldName fn)
                                                 (.kind "UNIT")
                                                 (.dataSourceName (.getName datomic-data-source))
                                                 (.build)
                                                 (doto (.addDependency api-schema)
                                                   (.addDependency datomic-data-source)))))
          configure-js-pipeline-resolver (fn [type-name field-name code]
                                           (let [tn (name type-name)
                                                 fn (name field-name)]
                                             (-> (CfnResolver$Builder/create stack (str "pipeline-resolver-" tn "-" fn))
                                                 (.apiId api-id)
                                                 (.typeName tn)
                                                 (.fieldName fn)
                                                 (.kind "PIPELINE")
                                                 (.pipelineConfig (-> (CfnResolver$PipelineConfigProperty/builder)
                                                                      ; no data source needed
                                                                      (.functions [])
                                                                      (.build)))
                                                 (.runtime (-> (CfnResolver$AppSyncRuntimeProperty/builder)
                                                               (.name "APPSYNC_JS")
                                                               (.runtimeVersion "1.0.0")
                                                               (.build)))
                                                 (.code code)
                                                 (.build)
                                                 (doto (.addDependency api-schema)
                                                   (.addDependency datomic-data-source)))))]
      ;; https://docs.aws.amazon.com/appsync/latest/devguide/utility-helpers-in-util.html
      (doseq [[parent-type-name field-name] @resolvers/resolvable-paths]
        (configure-datomic-resolver parent-type-name field-name))
      (doseq [op          ops/all-operations
              entity-type (framework/get-entity-types (d/db conn))
              :let [type-name  (::ops/parent-type op)
                    field-name (ops/gen-field-name op entity-type)]]
        (case (::ops/resolver op)
          ::ops/datomic (configure-datomic-resolver
                         type-name
                         field-name)
          ::ops/js-file (configure-js-pipeline-resolver
                         type-name
                         field-name
                         (slurp (io/resource (-> op ::ops/resolver-options ::ops/file))))
          :none)))))