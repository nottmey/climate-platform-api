(ns shared.operations.operation)

; TODO prefix, parent-type, and resolver-location could just as-well be data of an operation
(defprotocol Operation
  "Defines the configuration and behavior of a mutation/operation/subscription on all dynamic entities.

   Remember: Entity object and input types will always be generated."
  (get-graphql-parent-type [this] "Returns GraphQL parent type.")
  (gen-graphql-field [this entity fields] "Generates GraphQL field data for a given entity type.")
  (gen-graphql-object-types [this entity] "Generates all operation individual object types. Returns `nil`, if there are none.")
  ; TODO resolves-graphql-field? can be replaced by generated index (since we fetch the schema anyway)
  (resolves-graphql-field? [this field-name] "Returns whether a specific (entity) field belongs to this operation.")
  (get-resolver-location [this] "Returns :datomic when `resolve-field-data` should be used,
                                 returns :js-resolver when `get-js-resolver-code` should be used,
                                 else assumes empty resolver.")
  ; TODO fetch schema upfront resolve-field-data (allows caching) and pass it into it (since we fetch it anyway)
  (resolve-field-data [this args] "Resolves entity field data for specific field matched by `resolves-graphql-field?`.")
  (get-js-resolver-code [this] "Returns JavaScript resolver code to be used inside app sync to resolve a field.
                                Docs: https://docs.aws.amazon.com/appsync/latest/devguide/resolver-reference-js-version.html"))
