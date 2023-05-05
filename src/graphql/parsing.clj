(ns graphql.parsing
  (:require [clojure.test :refer [deftest is]])
  (:import (graphql.language Argument Document Field NullValue ObjectField ObjectValue OperationDefinition SelectionSet StringValue)
           (graphql.parser Parser)
           (java.util List Map)))

(defn- remove-nil-vals [m]
  (->> (for [[k v] m
             :when (nil? v)]
         k)
       (apply dissoc m)))

(comment
  (remove-nil-vals {"something" nil
                    "x" 1}))

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
        remove-nil-vals))
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
        remove-nil-vals))
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

(defn parse [^String query]
  (extract (Parser/parse query)))

(deftest parse-test
  (is (= (parse "mutation PublishCreatedPlanetaryBoundary {
              publishCreatedPlanetaryBoundary(value: {id: \"63530d29-0934-463d-a523-f3dd8eefdcea\", name: \" :platform/name sample value\\n\", quantifications: null}) { id name quantifications }
          }")
         [{:name      "PublishCreatedPlanetaryBoundary",
           :operation :mutation,
           :selection [{:name      "publishCreatedPlanetaryBoundary",
                        :arguments [{:name  "value",
                                     :value [{:name  "id",
                                              :value "63530d29-0934-463d-a523-f3dd8eefdcea"}
                                             {:name  "name",
                                              :value " :platform/name sample value\n"}
                                             {:name "quantifications",
                                              :value nil}]}],
                        :selection [{:name "id"}
                                    {:name "name"}
                                    {:name "quantifications"}]}]}])))