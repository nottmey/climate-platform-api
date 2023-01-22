(ns shared.operations.operation)

(defprotocol
  Operation
  (get-graphql-parent-type [this] "Returns GraphQL parent type.")
  (gen-graphql-field [this entity] "Generates GraphQL field data for a given entity type."))