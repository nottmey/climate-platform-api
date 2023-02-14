(ns shared.operations
  (:require
   [shared.operations.create :as create]
   [shared.operations.delete :as delete]
   [shared.operations.get :as get]
   [shared.operations.list :as list]
   [shared.operations.replace :as replace]))

(defn all []
  [(get/query)
   (list/query)
   (create/mutation)
   (replace/mutation)
   (delete/mutation)])

(comment
  (time (all)))