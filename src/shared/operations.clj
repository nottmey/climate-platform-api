(ns shared.operations
  (:require
   [clojure.test :refer [deftest is]]
   [shared.operations.create :as create]
   [shared.operations.delete :as delete]
   [shared.operations.get :as get]
   [shared.operations.list :as list]
   [shared.operations.on-created :as on-created]
   [shared.operations.on-deleted :as on-deleted]
   [shared.operations.on-updated :as on-updated]
   [shared.operations.operation :as o]
   [shared.operations.publish-created :as publish-created]
   [shared.operations.publish-deleted :as publish-deleted]
   [shared.operations.publish-updated :as publish-updated]
   [shared.operations.replace :as replace]))

(defn all [resolver-location]
  (->> [(get/query)
        (list/query)
        (create/mutation)
        (replace/mutation)
        (delete/mutation)
        (publish-created/mutation)
        (publish-updated/mutation)
        (publish-deleted/mutation)
        (on-created/subscription)
        (on-updated/subscription)
        (on-deleted/subscription)]
       (filter #(or (= resolver-location :any)
                    (= (o/get-resolver-location %) resolver-location)))))

(deftest all-test
  (is (< 0 (count (all :any))))
  (is (< 0 (count (all :datomic))))
  (is (every?
       #(= (o/get-resolver-location %) :datomic)
       (all :datomic))))

(comment
  (all :any))