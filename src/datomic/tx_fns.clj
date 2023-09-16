(ns datomic.tx-fns
  (:require [clojure.test :refer [deftest is]]
            [datomic.access :as access]
            [datomic.attributes :as attributes]
            [datomic.client.api :as d]
            [datomic.ion :as ion]
            [datomic.temp :as temp])
  (:import (clojure.lang ExceptionInfo)))

#_(datomic.ion/cancel {:cognitect.anomalies/category :cognitect.anomalies/incorrect #_:cognitect.anomalies/conflict
                       :cognitect.anomalies/message  "User map must contain :email and :name"})
(defn cancel [args]
  (ion/cancel args))

(defn create-type
  ([db type-name] (create-type db type-name (str "type-" type-name)))
  ([db type-name type-tid] (create-type db type-name type-tid (str "collection-" type-name)))
  ([_ type-name type-tid collection-tid]
   ; TODO cancel with conflict on already existing type-name
   [{:db/id                   type-tid
     :graphql.type/name       type-name
     :graphql.type/collection {:db/id  collection-tid
                               :db/doc (str "Entity collection of initial type '" type-name "'.")}}]))

(comment
  (d/transact (access/get-connection access/dev-env-db-name)
              {:tx-data ['(datomic.tx-fns/create-type "PlanetaryBoundary")]}))

(deftest create-type-test
  (let [conn     (temp/conn)
        selector '[{:graphql.type/collection [:db/doc
                                              {:graphql.collection/entities [:db/doc]}]}
                   {:graphql.field/_target [:db/doc]}]]
    (d/transact conn {:tx-data attributes/graphql-attributes})

    (let [result (d/transact conn {:tx-data (create-type (d/db conn) "LonelyType")})]
      (is (= 4 (count (:tx-data result)))))
    (is (= {:graphql.type/collection #:db{:doc "Entity collection of initial type 'LonelyType'."}}
           (d/pull (d/db conn) selector [:graphql.type/name "LonelyType"])))

    (let [result (d/transact
                  conn
                  {:tx-data (concat
                             (create-type (d/db conn) "SomeType" "type-tid" "collection-tid")
                             [{:graphql.field/target "type-tid"
                               :db/doc               "some field"}
                              {:db/id                       "collection-tid"
                               :graphql.collection/entities [{:db/id  "some entity"
                                                              :db/doc "some entity"}]}])})]
      (is (= 8 (count (:tx-data result)))))
    (is (= {:graphql.type/collection {:db/doc                      "Entity collection of initial type 'SomeType'."
                                      :graphql.collection/entities [{:db/doc "some entity"}]},
            :graphql.field/_target   [{:db/doc "some field"}]}
           (d/pull (d/db conn) selector [:graphql.type/name "SomeType"])))))

; TODO reuses collection of other type, but doesn't have any fields (besides id)
(defn create-type-alias [db])

(defn add-field
  ([db type field-name attribute]
   (add-field db type field-name attribute nil))
  ([db type field-name attribute target-type]
   (add-field db type field-name attribute target-type nil))
  ([db type field-name attribute target-type backwards-ref?]
   (add-field db type field-name attribute target-type backwards-ref? (str "field-" field-name)))
  ([db type field-name attribute target-type backwards-ref? field-tid]
   (let [attr-map   (d/pull db '[:db/ident :db/valueType] attribute)
         attr-ident (get attr-map :db/ident)
         attr-type  (get-in attr-map [:db/valueType :db/ident])]
     (cond
       (and (= attr-type :db.type/ref) (nil? target-type))
       (cancel {:cognitect.anomalies/category :cognitect.anomalies/incorrect
                :cognitect.anomalies/message  (str "When providing ref attribute " attr-ident ", the target-type needs to be not nil.")})

       (and (not (nil? target-type)) (not= attr-type :db.type/ref))
       (cancel {:cognitect.anomalies/category :cognitect.anomalies/incorrect
                :cognitect.anomalies/message  (str "When providing a target-type, the attribute " attr-ident " needs to be of type ref.")})

       (->> (-> (d/pull db '[{:graphql.type/fields [:graphql.field/name]}] type)
                :graphql.type/fields)
            (filter (fn [{:keys [:graphql.field/name]}] (= name field-name)))
            (first))
       (cancel {:cognitect.anomalies/category :cognitect.anomalies/conflict
                :cognitect.anomalies/message  (str "Field " field-name " already exists on given type.")})

       :else
       [[:db/add type :graphql.type/fields field-tid]
        (merge
         {:db/id                   field-tid
          :graphql.field/name      field-name
          :graphql.field/attribute attribute}
         (when target-type
           {:graphql.field/target         target-type
            :graphql.field/backwards-ref? (if (nil? backwards-ref?)
                                            false
                                            backwards-ref?)}))]))))

(comment
  (d/transact (access/get-connection access/dev-env-db-name)
              {:tx-data ['(datomic.tx-fns/add-field [:graphql.type/name "PlanetaryBoundary"] "description" :platform/description)]}))

(declare thrown-with-msg?)
(deftest add-field-test
  (let [conn        (temp/conn)
        pull-fields (fn [db type]
                      (d/pull db '[{:graphql.type/fields [:graphql.field/name
                                                          {:graphql.field/attribute [:db/ident]}
                                                          {:graphql.field/target [:graphql.type/name]}
                                                          :graphql.field/backwards-ref?]}] type))
        some-type   [:graphql.type/name "SomeType"]
        other-type  [:graphql.type/name "OtherType"]]
    (d/transact conn {:tx-data attributes/graphql-attributes})
    (d/transact conn {:tx-data (create-type (d/db conn) "SomeType")})
    (d/transact conn {:tx-data (create-type (d/db conn) "OtherType")})
    (d/transact conn {:tx-data [{:db/ident       :example-ref
                                 :db/valueType   :db.type/ref
                                 :db/cardinality :db.cardinality/one}]})

    (is (thrown-with-msg?
         ExceptionInfo #"Unable to resolve entity"
         (d/transact conn {:tx-data (add-field (d/db conn) [:graphql.type/name "UnknownType"] "doc" :db/doc)})))

    (is (thrown-with-msg?
         ExceptionInfo #"target-type needs to be not nil"
         (add-field (d/db conn) some-type "ref" :example-ref)))

    (is (thrown-with-msg?
         ExceptionInfo #"attribute :db/doc needs to be of type ref"
         (add-field (d/db conn) some-type "nonRef" :db/doc other-type)))

    (let [tx-data (add-field (d/db conn) some-type "doc" :db/doc)
          {:keys [db-after]} (d/transact conn {:tx-data tx-data})]
      (is (= (pull-fields db-after some-type)
             {:graphql.type/fields [{:graphql.field/name      "doc"
                                     :graphql.field/attribute {:db/ident :db/doc}}]})))

    (is (thrown-with-msg?
         ExceptionInfo #"Field doc already exists on given type"
         (add-field (d/db conn) some-type "doc" :db/doc)))

    (let [tx-data (add-field (d/db conn) some-type "ref" :example-ref other-type)
          {:keys [db-after]} (d/transact conn {:tx-data tx-data})]
      (is (= (-> (pull-fields db-after some-type)
                 (update :graphql.type/fields #(filter (fn [{:keys [graphql.field/name]}] (= name "ref")) %)))
             {:graphql.type/fields [{:graphql.field/name           "ref"
                                     :graphql.field/attribute      {:db/ident :example-ref}
                                     :graphql.field/target         {:graphql.type/name "OtherType"}
                                     :graphql.field/backwards-ref? false}]})))

    (let [tx-data (add-field (d/db conn) some-type "backRef" :example-ref other-type true)
          {:keys [db-after]} (d/transact conn {:tx-data tx-data})]
      (is (= (-> (pull-fields db-after some-type)
                 (update :graphql.type/fields #(filter (fn [{:keys [graphql.field/name]}] (= name "backRef")) %)))
             {:graphql.type/fields [{:graphql.field/name           "backRef"
                                     :graphql.field/attribute      {:db/ident :example-ref}
                                     :graphql.field/target         {:graphql.type/name "OtherType"}
                                     :graphql.field/backwards-ref? true}]})))

    (let [tx-data (concat
                   [[:db/add other-type :graphql.type/fields "tempid"]]
                   (add-field (d/db conn) some-type "otherDoc" :db/doc nil nil "tempid"))
          {:keys [db-after tempids]} (d/transact conn {:tx-data tx-data})]
      (is (= (-> (d/pull db-after '[:graphql.field/name
                                    {:graphql.field/attribute [:db/ident]}
                                    {:graphql.field/target [:graphql.type/name]}
                                    :graphql.field/backwards-ref?
                                    {:graphql.type/_fields [:graphql.type/name]}] (get tempids "tempid"))
                 (update :graphql.type/_fields set))
             {:graphql.field/name      "otherDoc"
              :graphql.field/attribute {:db/ident :db/doc}
              :graphql.type/_fields    #{{:graphql.type/name "SomeType"}
                                         {:graphql.type/name "OtherType"}}})))))

; TODO
(defn deprecate-type [db])

; TODO
(defn deprecate-field [db])

; TODO deprecate + add alias + add fields
(defn rename-type [db])

; TODO including new target (can only be done with new name)
(defn rename-field [db])

; TODO merge types = move all entities into one collection, deprecate one type and update collection (composite action)
; TODO move entity into collection of other type
; TODO split types = move entity into new type with new fields (composite action)
