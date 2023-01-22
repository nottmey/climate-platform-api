(ns shared.operations
  (:require [shared.operations.get :refer [get-query]]
            [shared.operations.list :refer [list-query]]))

(def all [get-query
          list-query])