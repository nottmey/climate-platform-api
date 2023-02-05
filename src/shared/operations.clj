(ns shared.operations
  (:require
   [shared.operations.create :refer [create-mutation]]
   [shared.operations.delete :refer [delete-mutation]]
   [shared.operations.get :refer [get-query]]
   [shared.operations.list :refer [list-query]]
   [shared.operations.replace :refer [replace-mutation]]))

(defn all []
  [(get-query)
   (list-query)
   (create-mutation)
   (replace-mutation)
   (delete-mutation)])

(comment
  (time (all)))