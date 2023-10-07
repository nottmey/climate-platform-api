(ns graphql.parsing
  (:require [clojure.test :refer [deftest is]]
            [utils :as utils])
  (:import (graphql.language
            Argument
            ArrayValue
            Document
            Field
            NullValue
            ObjectField
            ObjectValue
            OperationDefinition
            SelectionSet
            StringValue)
           (graphql.parser Parser)
           (java.util List Map)))

(defprotocol AstData
  (extract [this] "Returns Java implemented GraphQL AST as data."))

(extend-protocol AstData
  Document
  (extract [d] (-> d (.getDefinitions) extract))
  OperationDefinition
  (extract [o]
    (-> {:name       (.getName o)
         :operation  (.getOperation o)
         :variables  (.getVariableDefinitions o)
         :directives (.getDirectives o)
         :selection  (.getSelectionSet o)}
        extract
        utils/remove-nil-vals))
  SelectionSet
  (extract [s] (-> s (.getSelections) extract))
  Field
  (extract [f]
    (-> {:name       (.getName f)
         :alias      (.getAlias f)
         :arguments  (.getArguments f)
         :directives (.getDirectives f)
         :selection  (.getSelectionSet f)}
        extract
        utils/remove-nil-vals))
  Argument
  (extract [a]
    (-> {:name  (.getName a)
         :value (.getValue a)}
        extract))
  ObjectValue
  (extract [o] (-> o (.getObjectFields) extract))
  ObjectField
  (extract [o]
    (-> {:name  (.getName o)
         :value (.getValue o)}
        extract))
  ArrayValue
  (extract [a] (-> a (.getValues) extract))
  StringValue
  (extract [s] (-> s (.getValue)))
  NullValue
  (extract [_] nil)
  Enum
  (extract [o] (keyword (.toLowerCase (.name o))))
  List
  (extract [l] (not-empty (->> l (map extract) (into []))))
  Map
  (extract [m] (not-empty (update-vals m extract)))
  String
  (extract [s] s)
  nil
  (extract [n] n))

(defn parse [^String document]
  (extract (Parser/parse document)))

(deftest parse-test
  (is (= (parse "mutation PublishCreatedPlanetaryBoundary {
              publishCreatedPlanetaryBoundary(value: {id: \"63530d29-0934-463d-a523-f3dd8eefdcea\", name: \" :platform/name sample value\\n\", quantifications: [{id: \"4fb90656-388e-4e49-9d50-3f43ebc1b870\"}]}) { id name quantifications }
          }")
         [{:name      "PublishCreatedPlanetaryBoundary",
           :operation :mutation,
           :selection [{:name      "publishCreatedPlanetaryBoundary",
                        :arguments [{:name  "value",
                                     :value [{:name  "id",
                                              :value "63530d29-0934-463d-a523-f3dd8eefdcea"}
                                             {:name  "name",
                                              :value " :platform/name sample value\n"}
                                             {:name  "quantifications",
                                              :value [[{:name  "id"
                                                        :value "4fb90656-388e-4e49-9d50-3f43ebc1b870"}]]}]}],
                        :selection [{:name "id"}
                                    {:name "name"}
                                    {:name "quantifications"}]}]}])))

(defn valid? [^String document]
  (Parser/parse document)
  true)
