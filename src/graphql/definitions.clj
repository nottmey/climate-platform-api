(ns graphql.definitions
  (:refer-clojure :rename {name k-name})
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]))

; specification https://spec.graphql.org/June2018/#sec-Schema

(def tab-spaces "    ")

(def name-regex #"[_A-Za-z][_0-9A-Za-z]*")

(def generated-comment "#generated, do not edit manually!\n")

; https://spec.graphql.org/June2018/#Name
(defn valid-name? [k-or-s]
  (and (not (nil? k-or-s))
       (boolean (re-matches name-regex (k-name k-or-s)))
       (not (str/starts-with? (k-name k-or-s) "__"))))

(def operation-types
  #{:query :mutation :subscription})

; https://spec.graphql.org/June2018/#OperationType
(defn valid-operation-type? [k-or-s]
  (boolean (operation-types (keyword k-or-s))))

(comment
  (valid-name? "_")
  (valid-name? "A_"))

; https://spec.graphql.org/June2018/#Type
(defn type-ref-definition [{:keys [type list? required-type? required-list?]}]
  {:pre [(valid-name? type)
         (Character/isUpperCase ^char (first (k-name type)))]}
  (let [list? (or list? required-list?)]
    (str (when list? "[") (k-name type) (when required-type? "!") (when list? "]") (when required-list? "!"))))

(defn default-value-definition [default-value]
  {:pre [(or (nil? default-value)
             (int? default-value))]}
  (when default-value
    (str " = " default-value)))

; https://spec.graphql.org/June2018/#InputValueDefinition
; add additional default value types if needed
(defn input-value-definition [{:keys [name default-value type]
                               :as   args-with-type-ref}]
  {:pre [(valid-name? name)
         (or (not default-value)
             (= (k-name type) "Int"))]}
  (str (k-name name)
       ": "
       (type-ref-definition args-with-type-ref)
       (default-value-definition default-value)))

(comment
  (input-value-definition {:name           "id"
                           :type           :Int
                           :default-value  0
                           :list?          true
                           :required-type? true
                           :required-list? true}))

; https://spec.graphql.org/June2018/#ArgumentsDefinition
(defn arguments-definition [{:keys [arguments]}]
  {:pre [(sequential? arguments)
         (pos? (count arguments))]}
  (let [arguments-def (->> arguments
                           (map input-value-definition)
                           (str/join ", "))]
    (str "(" arguments-def ")")))

(comment
  (arguments-definition {:arguments [{:name :id
                                      :type :ID}
                                     {:name :database
                                      :type :String}]}))

; https://spec.graphql.org/June2018/#FieldDefinition
(defn field-definition [{:keys [name type arguments default-value docstring directive]
                         :as   args-with-type-ref}]
  {:pre [(valid-name? name)
         (or (nil? arguments) (pos? (count arguments)))
         (or (not default-value) (= (k-name type) "Int"))]}
  (str (when docstring
         (str "\"" docstring "\"" "\n" tab-spaces))
       (k-name name) (when arguments (arguments-definition {:arguments arguments}))
       ": "
       (type-ref-definition args-with-type-ref)
       (default-value-definition default-value)
       (when directive
         ; clearly separated to the next field with an additional \n
         (str "\n" tab-spaces directive))))

(deftest field-definition-test
  (is (= "get(id: Int! = 1, database: String): Result"
         (field-definition {:name      :get
                            :type      :Result
                            :arguments [{:name           :id
                                         :type           :Int
                                         :default-value  1
                                         :required-type? true}
                                        {:name :database
                                         :type :String}]})))
  (is (= "size: Int = 20"
         (field-definition {:name          :size
                            :type          :Int
                            :default-value 20})))

  (is (= (str
          "\"Some documentation with `code` and punctuation.\"\n"
          "    onUpdatedPlanetaryBoundary(id: ID, name: String): PlanetaryBoundary!\n"
          "    @aws_subscribe(mutations: [\"publishUpdatedPlanetaryBoundary\"])")
         (field-definition
          {:docstring      "Some documentation with `code` and punctuation."
           :name           :onUpdatedPlanetaryBoundary
           :arguments      [{:name :id
                             :type :ID}
                            {:name :name
                             :type :String}]
           :type           :PlanetaryBoundary
           :required-type? true
           :directive      "@aws_subscribe(mutations: [\"publishUpdatedPlanetaryBoundary\"])"}))))

; https://spec.graphql.org/June2018/#FieldsDefinition
(defn field-list-definition [{:keys [spaced? fields]}]
  {:pre [(sequential? fields)
         (pos? (count fields))]}
  (->> fields
       (map field-definition)
       (map #(str tab-spaces % "\n"))
       (str/join (if spaced? "\n" ""))))

(comment
  (printf (field-list-definition {:fields [{:name :query
                                            :type :Query}
                                           {:name :mutation
                                            :type :Mutation}]})))

; https://spec.graphql.org/June2018/#SchemaDefinition
(defn schema-definition [{:keys [root-ops]}]
  {:pre [(every? valid-operation-type? (keys root-ops))
         (every? valid-name? (vals root-ops))]}
  (let [fields     (->> root-ops (map (fn [[f t]] {:name f
                                                   :type t})))
        fields-def (field-list-definition {:fields fields})]
    (str
     generated-comment
     "schema {\n" fields-def "}\n\n")))

(comment
  (printf (schema-definition {:root-ops {:query        :Query
                                         :mutation     :Mutation
                                         :subscription :Subscription}})))

; https://spec.graphql.org/June2018/#ObjectTypeDefinition
(defn object-type-definition [{:keys [name spaced-fields? fields interfaces]}]
  {:pre [(valid-name? name)
         (or (nil? interfaces)
             (pos? (count interfaces)))]}
  (let [fields-def     (field-list-definition {:spaced? spaced-fields?
                                               :fields  fields})
        implements-def (when interfaces
                         (->> interfaces
                              (map k-name)
                              (str/join " & ")
                              (str " implements ")))]
    (str
     generated-comment
     "type " (k-name name) implements-def " {\n" fields-def "}\n\n")))

(comment
  (printf (object-type-definition {:name       :Query
                                   :interfaces [:Attribute :Other]
                                   :fields     [{:name           :databases
                                                 :type           :ID
                                                 :list?          true
                                                 :required-type? true}]}))
  (printf (object-type-definition {:name   :Query
                                   :fields [{:name           :get
                                             :arguments      [{:name           :id
                                                               :type           :ID
                                                               :required-type? true}]
                                             :type           :ID
                                             :list?          true
                                             :required-type? true
                                             :required-list? true}]})))

; https://spec.graphql.org/June2018/#InterfaceTypeDefinition
(defn interface-type-definition [{:keys [name fields]}]
  {:pre [(valid-name? name)]}
  (let [fields-def (field-list-definition {:fields fields})]
    (str
     generated-comment
     "interface " (k-name name) " {\n" fields-def "}\n\n")))

(comment
  (printf (interface-type-definition {:name   :Attribute
                                      :fields [{:name :id
                                                :type :ID}]})))

; https://spec.graphql.org/June2018/#InputObjectTypeDefinition
(defn input-object-type-definition [{:keys [name fields]}]
  {:pre [valid-name? name]}
  (let [fields-def (field-list-definition {:fields fields})]
    (str
     generated-comment
     "input " (k-name name) " {\n" fields-def "}\n\n")))

(comment
  (printf (input-object-type-definition {:name   :EntityFilter
                                         :fields [{:name :attribute
                                                   :type :ID}]}))
  (printf (input-object-type-definition {:name   :Query
                                         :fields [{:name          :size
                                                   :type          :Int
                                                   :default-value 20}]})))

; remember, there are more:
; ScalarTypeDefinition
; UnionTypeDefinition
; EnumTypeDefinition
