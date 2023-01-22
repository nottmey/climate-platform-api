(ns shared.operations
  (:require [shared.operations.get :refer [get-query]]
            [shared.operations.list :refer [list-query]]
            [shared.operations.create :refer [create-mutation]]
            [shared.operations.replace :refer [replace-mutation]]
            [shared.operations.delete :refer [delete-mutation]]))

(def all [get-query
          list-query
          create-mutation
          replace-mutation
          delete-mutation])