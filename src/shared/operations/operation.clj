(ns shared.operations.operation)

(defprotocol Operation
  "Defines the configuration and behavior of a mutation/operation/subscription on all dynamic entities.

   Remember: Entity object and input types will always be generated."
  (get-graphql-parent-type [this] "Returns GraphQL parent type.")
  (gen-graphql-field [this entity] "Generates GraphQL field data for a given entity type.")
  (gen-graphql-object-types [this entity] "Generates all operation individual object types. Returns `nil`, if there are none.")
  ; TODO resolves-graphql-field? can be replaced by generated index (since we fetch the schema anyway)
  (resolves-graphql-field? [this field-name] "Returns whether a specific (entity) field belongs to this operation.")
  (resolve-field-data [this conn resolver-args] "Resolves entity field data for specific field matched by `resolves-graphql-field?`."))
