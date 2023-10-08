(ns datomic.framework
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [clojure.walk :as walk]
   [datomic.access :as access]
   [datomic.client.api :as d]
   [ions.logging :as logging]
   [shared.mappings :as sa]
   [testing :as t]))

(defn- correct-attr-ident [attribute backref?]
  (let [normal-attr-ident (:db/ident attribute)]
    (if backref?
      (keyword (namespace normal-attr-ident) (str "_" (name normal-attr-ident)))
      normal-attr-ident)))

(defn get-schema [db]
  (let [base (->> (d/q '[:find (pull ?type [* {:graphql.type/fields [*
                                                                     {:graphql.field/attribute [*]}
                                                                     {:graphql.field/target [*]}]}])
                         :where [?type :graphql.type/name]]
                       db)
                  (map first))]
    {::types      (->> base
                       (map (fn [m] (update m :graphql.type/fields #(->> %
                                                                         (map (fn [{:keys [graphql.field/name]
                                                                                    :as   field}]
                                                                                [name field]))
                                                                         (into {})))))
                       (map (fn [{:keys [graphql.type/name]
                                  :as   type}]
                              [name type]))
                       (into {}))
     ::attributes (->> base
                       (map (fn [{:keys [graphql.type/name
                                         graphql.type/fields]}]
                              [name
                               (reduce
                                (fn [m {:keys [graphql.field/attribute
                                               graphql.field/backwards-ref?]
                                        :as   field}]
                                  (let [attr-ident (correct-attr-ident attribute backwards-ref?)]
                                    (-> m
                                        (assoc attr-ident attribute)
                                        (update-in
                                         [attr-ident :graphql.field/_attribute]
                                         conj
                                         (dissoc field :graphql.field/attribute)))))
                                {}
                                fields)]))
                       (into {}))}))

(comment
  (get-schema (d/db (access/get-connection access/dev-env-db-name)))

  (get-schema (t/temp-db)))

(defn get-entity-types [db]
  (sort (keys (::types (get-schema db)))))

(comment
  (get-entity-types (t/temp-db)))

(defn get-collection [schema entity-type]
  (get-in schema [::types entity-type :graphql.type/collection :db/id]))

(comment
  (get-collection (get-schema (t/temp-db)) "PlanetaryBoundary"))

(defn get-collection-id [conn entity-type-name]
  (-> (d/pull (d/db conn) '[:graphql.type/collection] [:graphql.type/name entity-type-name])
      (get-in [:graphql.type/collection :db/id])))

(comment
  (get-collection-id (t/temp-conn) "PlanetaryBoundary"))

(defn get-default-paths [schema entity-type]
  (->> (vals (get-in schema [::types entity-type :graphql.type/fields]))
       (map (fn [{:keys [graphql.field/name
                         graphql.field/target]}]
              (if target
                (str name "/id")
                name)))
       (concat ["id"])
       (sort)))

(comment
  (get-default-paths (get-schema (t/temp-db)) "PlanetaryBoundary"))

(deftest get-default-paths-test
  (let [schema (get-schema (t/temp-db))
        paths  (get-default-paths schema "PlanetaryBoundary")]
    (is (= #{"id" "name" "description" (str "quantifications" "/id")}
           (set paths)))))

(defn resolve-input-fields [schema input-obj entity-type]
  (let [temp-id    (get input-obj "id")
        uuid       (parse-uuid temp-id)
        input-obj  (dissoc input-obj "id")
        collection (get-collection schema entity-type)]
    (concat
     ; always add id
     [[:db/add collection :graphql.collection/entities temp-id]
      [:db/add temp-id :platform/id uuid]]
     ; generate adds for input-obj
     (for [[field value] input-obj
           :when (not (nil? value))
           :let [values       (if (sequential? value) value [value])
                 field-config (get-in schema [::types entity-type :graphql.type/fields field])
                 {:keys [graphql.field/attribute
                         graphql.field/target
                         graphql.field/backwards-ref?]} field-config
                 attr-ident   (:db/ident attribute)
                 target-type  (:graphql.type/name target)]
           :when (not (nil? attr-ident))
           value values]
       (if (and target-type (map? value))
         (if backwards-ref?
           [:db/add (get value "id") attr-ident temp-id]
           [:db/add temp-id attr-ident (get value "id")])
         [:db/add temp-id attr-ident value]))
     ; generate next level, if available
     (apply
      concat
      (for [[field value] input-obj
            :when (not (nil? value))
            :let [values       (if (sequential? value) value [value])
                  field-config (get-in schema [::types entity-type :graphql.type/fields field])
                  {:keys [graphql.field/target]} field-config
                  target-type  (:graphql.type/name target)]
            value values
            :when (and target-type (map? value))]
        (resolve-input-fields schema value target-type))))))

(deftest resolve-input-fields-test
  (let [conn (t/temp-conn)
        cid  (get-collection-id conn "PlanetaryBoundary")]
    (is (= [[:db/add cid :graphql.collection/entities "00000000-0000-0000-0000-000000000001"]
            [:db/add "00000000-0000-0000-0000-000000000001" :platform/id #uuid"00000000-0000-0000-0000-000000000001"]
            [:db/add "00000000-0000-0000-0000-000000000001" :platform/name "PlanetaryBoundary"]]
           (resolve-input-fields
            (get-schema (d/db conn))
            {"id"                        "00000000-0000-0000-0000-000000000001"
             "name"                      "PlanetaryBoundary"
             "anyNilFieldDoesntShowUp"   nil
             "anyEmptyFieldDoesntShowUp" []}
            "PlanetaryBoundary"))))

  (let [conn   (t/temp-conn)
        pb-cid (get-collection-id conn "PlanetaryBoundary")
        q-cid  (get-collection-id conn "Quantification")]
    (is (= [[:db/add pb-cid :graphql.collection/entities "00000000-0000-0000-0000-000000000001"]
            [:db/add "00000000-0000-0000-0000-000000000001" :platform/id #uuid"00000000-0000-0000-0000-000000000001"]
            [:db/add "00000000-0000-0000-0000-000000000001" :platform/name "PlanetaryBoundary"]
            [:db/add "00000000-0000-0000-0000-000000000001" :planetary-boundary/quantifications "00000000-0000-0000-0000-000000000002"]
            [:db/add "00000000-0000-0000-0000-000000000001" :planetary-boundary/quantifications "00000000-0000-0000-0000-000000000003"]
            [:db/add q-cid :graphql.collection/entities "00000000-0000-0000-0000-000000000002"]
            [:db/add "00000000-0000-0000-0000-000000000002" :platform/id #uuid"00000000-0000-0000-0000-000000000002"]
            [:db/add "00000000-0000-0000-0000-000000000002" :platform/name "Quantification"]
            [:db/add q-cid :graphql.collection/entities "00000000-0000-0000-0000-000000000003"]
            [:db/add "00000000-0000-0000-0000-000000000003" :platform/id #uuid"00000000-0000-0000-0000-000000000003"]
            [:db/add "00000000-0000-0000-0000-000000000003" :platform/name "Quantification2"]]
           (resolve-input-fields
            (get-schema (d/db conn))
            {"id"              "00000000-0000-0000-0000-000000000001"
             "name"            "PlanetaryBoundary"
             "quantifications" [{"id"   "00000000-0000-0000-0000-000000000002"
                                 "name" "Quantification"}
                                {"id"   "00000000-0000-0000-0000-000000000003"
                                 "name" "Quantification2"}]}
            "PlanetaryBoundary")))))

(defn app-sync-path->attribute-path [schema entity-type app-sync-path]
  (loop [[current-field & next-fields] (str/split app-sync-path #"/")
         current-type entity-type
         result       []]
    (if (nil? current-field)
      result
      (let [{:keys [graphql.field/target
                    graphql.field/attribute
                    graphql.field/backwards-ref?]}
            (get-in schema [::types current-type :graphql.type/fields current-field])
            attr-type (get-in attribute [:db/valueType :db/ident])]
        (recur
         (if (and (nil? next-fields) (= attr-type :db.type/ref))
           ; path to ref without explicitly specifying id implicitly becomes path to id of the referenced object
           ["id"]
           next-fields)
         (:graphql.type/name target)
         (conj
          result
          (if (= current-field "id")
            :platform/id
            (correct-attr-ident attribute backwards-ref?))))))))

(defn gen-pull-pattern [schema entity-type selected-paths]
  (logging/info "GeneratingPullPattern" {:paths (vec selected-paths)})
  (let [pattern (->> selected-paths
                     ; assoc-in all paths as attribute seq into a map, with empty maps as leafs
                     (reduce
                      (fn [merged-attribute-paths app-sync-path]
                        (let [attribute-path (app-sync-path->attribute-path schema entity-type app-sync-path)]
                          (assoc-in merged-attribute-paths attribute-path {})))
                      {})
                     ; cleanup empty maps by moving the respective entry into a list next to the filled maps
                     (walk/postwalk
                      (fn [form]
                        (if (and (map? form) (seq form))
                          (let [{value-attributes true
                                 ref-attributes   false} (group-by #(empty? (second %)) form)]
                            (concat
                             (map first value-attributes)
                             (when (some? ref-attributes)
                               [(into {} ref-attributes)])))
                          form)))
                     vec)]
    (logging/info "UsingPullPattern" {:pattern (str pattern)})
    pattern))

(deftest gen-pull-pattern-test
  (let [conn    (t/temp-conn)
        schema  (get-schema (d/db conn))
        paths   #{"id"
                  "name"
                  "quantifications"
                  "quantifications/id"
                  "quantifications/name"
                  "quantifications/planetaryBoundaries"
                  "quantifications/planetaryBoundaries/id"
                  "quantifications/planetaryBoundaries/name"}
        pattern (gen-pull-pattern schema "PlanetaryBoundary" paths)]
    ; TODO use u/* symbols
    (d/transact conn {:tx-data (resolve-input-fields schema {"id"              "00000000-0000-0000-0000-000000000000"
                                                             "name"            "pb1"
                                                             "quantifications" [{"id"   "00000000-0000-0000-0000-000000000001"
                                                                                 "name" "q1"}
                                                                                {"id"   "00000000-0000-0000-0000-000000000002"
                                                                                 "name" "q2"}]} "PlanetaryBoundary")})
    (is (= {:platform/id                        #uuid"00000000-0000-0000-0000-000000000000",
            :platform/name                      "pb1",
            :planetary-boundary/quantifications [{:platform/name                       "q1",
                                                  :platform/id                         #uuid"00000000-0000-0000-0000-000000000001",
                                                  :planetary-boundary/_quantifications [#:platform{:id   #uuid"00000000-0000-0000-0000-000000000000",
                                                                                                   :name "pb1"}]}
                                                 {:platform/name                       "q2",
                                                  :platform/id                         #uuid"00000000-0000-0000-0000-000000000002",
                                                  :planetary-boundary/_quantifications [#:platform{:id   #uuid"00000000-0000-0000-0000-000000000000",
                                                                                                   :name "pb1"}]}]}
           (d/pull (d/db conn) pattern [:platform/id #uuid "00000000-0000-0000-0000-000000000000"])))))

(defn reverse-pull-pattern [schema entity-type selected-paths pulled-entity]
  (let [current-paths (->> selected-paths
                           (map #(first (str/split % #"/")))
                           (set))]
    (->> pulled-entity
         (mapcat
          (fn [[attribute datomic-value]]
            (if (= attribute :platform/id)
              [["id" (str datomic-value)]]
              (let [{:keys [graphql.field/_attribute
                            db/valueType
                            db/cardinality]} (get-in schema [::attributes entity-type attribute])
                    value-type  (:db/ident valueType)
                    cardinality (:db/ident cardinality)]
                (->> _attribute
                     (filter (fn [{:keys [graphql.field/name]}]
                               (contains? current-paths name)))
                     (map (fn [{:keys [graphql.field/name
                                       graphql.field/target
                                       graphql.field/backwards-ref?]}]
                            [name
                             (if (= value-type :db.type/ref)
                               (let [target-type          (:graphql.type/name target)
                                     sub-selected-paths   (->> selected-paths
                                                               (filter #(str/starts-with? % name))
                                                               (map #(str/join "/" (rest (str/split % #"/"))))
                                                               (set))
                                     apply-nested-reverse #(reverse-pull-pattern
                                                            schema
                                                            target-type
                                                            sub-selected-paths
                                                            %)]
                                 (if (or (= cardinality :db.cardinality/many)
                                         (and (= cardinality :db.cardinality/one)
                                              backwards-ref?))
                                   (map apply-nested-reverse datomic-value)
                                   (apply-nested-reverse datomic-value)))
                               (sa/->gql-value
                                datomic-value
                                value-type
                                cardinality))])))))))
         (into {}))))

(deftest reverse-pull-pattern-test
  (let [conn   (t/temp-conn)
        schema (get-schema (d/db conn))]
    ; TODO use u/* symbols
    (is (= {"id"              "00000000-0000-0000-0000-000000000000"
            "name"            "pb1"
            "quantifications" [{"name"                "q1",
                                "id"                  "00000000-0000-0000-0000-000000000001"
                                "planetaryBoundaries" [{"id"   "00000000-0000-0000-0000-000000000000"
                                                        "name" "pb1"}]}
                               {"name"                "q2"
                                "id"                  "00000000-0000-0000-0000-000000000002"
                                "planetaryBoundaries" [{"id"   "00000000-0000-0000-0000-000000000000"
                                                        "name" "pb1"}]}]}
           (reverse-pull-pattern
            schema
            "PlanetaryBoundary"
            #{"id"
              "name"
              "quantifications/id"
              "quantifications/name"
              "quantifications/planetaryBoundaries/id"
              "quantifications/planetaryBoundaries/name"}
            ; TODO use u/* symbols
            {:platform/id                        #uuid"00000000-0000-0000-0000-000000000000",
             :platform/name                      "pb1",
             :planetary-boundary/quantifications [{:platform/name                       "q1",
                                                   :platform/id                         #uuid"00000000-0000-0000-0000-000000000001",
                                                   :planetary-boundary/_quantifications [#:platform{:id   #uuid"00000000-0000-0000-0000-000000000000",
                                                                                                    :name "pb1"}]}
                                                  {:platform/name                       "q2",
                                                   :platform/id                         #uuid"00000000-0000-0000-0000-000000000002",
                                                   :planetary-boundary/_quantifications [#:platform{:id   #uuid"00000000-0000-0000-0000-000000000000",
                                                                                                    :name "pb1"}]}]})))))

(defn pull-and-resolve-entity-value [schema entity-uuid db entity-type selected-paths]
  (let [pattern       (gen-pull-pattern schema entity-type selected-paths)
        pulled-entity (d/pull db pattern [:platform/id entity-uuid])]
    (if (empty? pulled-entity)
      nil
      (reverse-pull-pattern schema entity-type selected-paths pulled-entity))))

(deftest pull-and-resolve-entity-test
  ; TODO use u/* symbols
  (let [data           {:platform/id                        #uuid"00000000-0000-0000-0000-000000000000"
                        :platform/name                      "pb1"
                        :planetary-boundary/quantifications [#:platform{:name "q1"
                                                                        :id   #uuid"00000000-0000-0000-0000-000000000001"}
                                                             #:platform{:name "q2"
                                                                        :id   #uuid"00000000-0000-0000-0000-000000000002"}]}
        {:keys [db-after]} (d/transact (t/temp-conn) {:tx-data [data]})
        selected-paths #{"id"
                         "name"
                         "quantifications/id"
                         "quantifications/name"
                         "quantifications/planetaryBoundaries/id"
                         "quantifications/planetaryBoundaries/name"}
        schema         (get-schema db-after)
        pulled-entity  (pull-and-resolve-entity-value schema #uuid"00000000-0000-0000-0000-000000000000" db-after "PlanetaryBoundary" selected-paths)]
    (is (= {"id"              "00000000-0000-0000-0000-000000000000"
            "name"            "pb1"
            "quantifications" [{"name"                "q1",
                                "id"                  "00000000-0000-0000-0000-000000000001"
                                "planetaryBoundaries" [{"id"   "00000000-0000-0000-0000-000000000000"
                                                        "name" "pb1"}]}
                               {"name"                "q2"
                                "id"                  "00000000-0000-0000-0000-000000000002"
                                "planetaryBoundaries" [{"id"   "00000000-0000-0000-0000-000000000000"
                                                        "name" "pb1"}]}]}
           pulled-entity))))
