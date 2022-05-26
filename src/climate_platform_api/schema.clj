(ns climate-platform-api.schema)

(def initial-attributes
  [{:db/ident       :time.slot/month
    :db/valueType   :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc         "Month (1-12)"}
   {:db/ident       :time.slot/year
    :db/valueType   :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc         "Year (e.g. 1991)"}
   {:db/ident       :time.slot/custom
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc         "Reference to a custom time slot"}
   {:db/ident       :time.slot.custom/consecutive-months
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity
    :db/doc         "Custom (generic) time slot in consecutive months (e.g. 'JJA' for June-July-August)"}
   {:db/ident       :time.slot.custom/consecutive-years
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity
    :db/doc         "Custom time slot in consecutive years (e.g. '2018-2022')"}
   {:db/ident       :value/temperature
    :db/valueType   :db.type/double
    :db/cardinality :db.cardinality/one
    :db/doc         "Temperature value"}
   {:db/ident       :value/unit
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc         "Reference to unit of value"}
   {:db/ident :value.unit/celsius}
   {:db/ident       :value/context
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc         "Reference to contextual detail of value"}
   {:db/ident :value.context/difference-to-base-period}
   {:db/ident       :value.generation/method
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc         "Reference to generation method of value"}
   {:db/ident :value.generation.method/aggregation}
   {:db/ident       :value.aggregation/method
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc         "Reference to aggregation method of value"}
   {:db/ident :value.aggregation.method/mean}
   {:db/ident       :value/dataset
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc         "Reference to dataset of value"}
   {:db/ident       :dataset/name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity
    :db/doc         "Identifying dataset name"}
   {:db/ident       :dataset/base-period
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc         "Reference to dataset base period (custom time slot)"}
   {:db/ident       :source/direct
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/doc         "References to direct sources"}
   {:db/ident       :source/underlying
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/doc         "References to underlying sources"}
   {:db/ident       :source/short-name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity
    :db/doc         "Identifying short name of source"}
   {:db/ident       :source/long-name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity
    :db/doc         "Identifying long name of source"}
   {:db/ident       :source/description
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         "Description of source"}
   {:db/ident       :source/homepage
    :db/valueType   :db.type/uri
    :db/cardinality :db.cardinality/one
    :db/doc         "Homepage of source"}])

(def valid-attribute-ident?
  (set (map :db/ident initial-attributes)))
