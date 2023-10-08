(ns utils
  "general clojure utilities"
  (:require [clojure.test :refer [*testing-vars* deftest is]]))

(defn remove-nil-vals [m]
  (->> (for [[k v] m
             :when (nil? v)]
         k)
       (apply dissoc m)))

(comment
  (remove-nil-vals {"something" nil
                    "x"         1}))

(defn test-mode? []
  (boolean (seq *testing-vars*)))

(deftest is-test-mode-on-test
  (is (test-mode?)))

(defn local-mode? "returns true, if we are in repl or testing" []
  (= (System/getProperty "local.mode") "true"))

(deftest is-local-mode-on-test
  (is (local-mode?)))
