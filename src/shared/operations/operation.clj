(ns shared.operations.operation)

(defprotocol
  Operation
  (get-graphql-parent-type [this] "Returns GraphQL parent type.")
  (gen-graphql-field [this type] "Generates GraphQL field data for a given entity type."))