(ns cdk.app-sync
  (:import (software.amazon.awscdk RemovalPolicy Stack)
           (software.amazon.awscdk.services.appsync CfnApiKey$Builder CfnDataSource$Builder CfnDataSource$DynamoDBConfigProperty CfnGraphQLApi$Builder CfnGraphQLSchema$Builder CfnResolver$Builder)
           (software.amazon.awscdk.services.dynamodb Attribute AttributeType BillingMode StreamViewType Table$Builder)
           (software.amazon.awscdk.services.iam ManagedPolicy Role$Builder ServicePrincipal)))

(defn app-sync [^Stack stack]
  (let [api    (-> (CfnGraphQLApi$Builder/create stack "climate-platform-api")
                   (.name "climate-platform-api")
                   (.authenticationType "API_KEY")
                   (.build))
        api-id (-> api (.getAttrApiId))]
    (-> (CfnApiKey$Builder/create stack "climate-platform-api-key")
        (.apiId api-id)
        (.build))
    ; tutorial https://github.com/aws-samples/aws-cdk-examples/blob/6c7efd82807d60648543393cc70a2a0343b1c0ec/typescript/appsync-graphql-dynamodb/index.ts
    (let [schema-template  "
            type items {
              itemId: ID!
              name: String
            }
            type Paginateditems {
              items: [items!]!
              nextToken: String
            }
            type Query {
              all(limit: Int, nextToken: String): Paginateditems!
              getOne(itemId: ID!): items
            }
            type Mutation {
              save(name: String!): items
              delete(itemId: ID!): items
            }
            type Schema {
              query: Query
              mutation: Mutation
            }"
          api-schema       (-> (CfnGraphQLSchema$Builder/create stack "climate-platform-api-schema")
                               (.apiId api-id)
                               (.definition schema-template)
                               (.build))
          items-table      (-> (Table$Builder/create stack "tutorial-table")
                               (.tableName "items")
                               (.partitionKey (-> (Attribute/builder)
                                                  (.name "itemId")
                                                  (.type AttributeType/STRING)
                                                  (.build)))
                               (.billingMode BillingMode/PAY_PER_REQUEST)
                               (.stream StreamViewType/NEW_IMAGE)
                               (.removalPolicy RemovalPolicy/DESTROY)
                               (.build))
          items-table-role (-> (Role$Builder/create stack "tutorial-table-role")
                               (.assumedBy (ServicePrincipal. "appsync.amazonaws.com"))
                               (.build)
                               (doto (.addManagedPolicy (ManagedPolicy/fromAwsManagedPolicyName "AmazonDynamoDBFullAccess"))))
          data-source      (-> (CfnDataSource$Builder/create stack "tutorial-table-datasource")
                               (.apiId api-id)
                               (.name "ItemsDynamoDataSource")
                               (.type "AMAZON_DYNAMODB")
                               (.dynamoDbConfig (-> (CfnDataSource$DynamoDBConfigProperty/builder)
                                                    (.tableName (.getTableName items-table))
                                                    (.awsRegion (.getRegion stack))
                                                    (.build)))
                               (.serviceRoleArn (.getRoleArn items-table-role))
                               (.build))]
      (-> (CfnResolver$Builder/create stack "get-one-tutorial-resolver")
          (.apiId api-id)
          (.typeName "Query")
          (.fieldName "getOne")
          (.dataSourceName (.getName data-source))
          (.requestMappingTemplate "{
                                      \"version\": \"2017-02-28\",
                                      \"operation\": \"GetItem\",
                                      \"key\": {
                                        \"itemId\": $util.dynamodb.toDynamoDBJson($ctx.args.itemId)
                                      }
                                    }")
          (.responseMappingTemplate "$util.toJson($ctx.result)")
          (.build)
          (doto (.addDependsOn api-schema)
                (.addDependsOn data-source)))
      (-> (CfnResolver$Builder/create stack "get-all-tutorial-resolver")
          (.apiId api-id)
          (.typeName "Query")
          (.fieldName "all")
          (.dataSourceName (.getName data-source))
          (.requestMappingTemplate "{
                                      \"version\": \"2017-02-28\",
                                      \"operation\": \"Scan\",
                                      \"limit\": $util.defaultIfNull($ctx.args.limit, 20),
                                      \"nextToken\": $util.toJson($util.defaultIfNullOrEmpty($ctx.args.nextToken, null))
                                    }")
          (.responseMappingTemplate "$util.toJson($ctx.result)")
          (.build)
          (doto (.addDependsOn api-schema)
                (.addDependsOn data-source)))
      (-> (CfnResolver$Builder/create stack "save-mutation-tutorial-resolver")
          (.apiId api-id)
          (.typeName "Mutation")
          (.fieldName "save")
          (.dataSourceName (.getName data-source))
          (.requestMappingTemplate "{
                                      \"version\": \"2017-02-28\",
                                      \"operation\": \"PutItem\",
                                      \"key\": {
                                        \"itemId\": { \"S\": \"$util.autoId()\" }
                                      },
                                      \"attributeValues\": {
                                        \"name\": $util.dynamodb.toDynamoDBJson($ctx.args.name)
                                      }
                                    }")
          (.responseMappingTemplate "$util.toJson($ctx.result)")
          (.build)
          (doto (.addDependsOn api-schema)
                (.addDependsOn data-source)))
      (-> (CfnResolver$Builder/create stack "delete-mutation-tutorial-resolver")
          (.apiId api-id)
          (.typeName "Mutation")
          (.fieldName "delete")
          (.dataSourceName (.getName data-source))
          (.requestMappingTemplate "{
                                      \"version\": \"2017-02-28\",
                                      \"operation\": \"DeleteItem\",
                                      \"key\": {
                                        \"itemId\": $util.dynamodb.toDynamoDBJson($ctx.args.itemId)
                                      }
                                    }")
          (.responseMappingTemplate "$util.toJson($ctx.result)")
          (.build)
          (doto (.addDependsOn api-schema)
                (.addDependsOn data-source))))))