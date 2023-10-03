(ns ions.one-to-many-ref-test
  (:require
   [clojure.test :refer [deftest is]]
   [utils.cloud-import :refer [call-resolver-with-local-conn]]
   [utils.uuid :refer [generate-uuid]]))

(deftest one-to-many-relation-test
  (let [breakdown-id  (generate-uuid)
        data-point-id (generate-uuid)]
    (is (= {"id"     breakdown-id
            "parent" {"id" data-point-id}}
           (call-resolver-with-local-conn
            "Mutation"
            "createBreakdown"
            ["id" "parent/id"]
            {"value" {"id"     breakdown-id
                      "parent" {"id" data-point-id}}})))

    (is (= {"id"     breakdown-id
            "parent" {"id"         data-point-id
                      "breakdowns" [{"id" breakdown-id}]}}
           (call-resolver-with-local-conn
            "Query"
            "getBreakdown"
            ["id" "parent/id" "parent/breakdowns/id"]
            {"id" breakdown-id})))

    (is (= {"id"         data-point-id
            "breakdowns" [{"id"     breakdown-id
                           "parent" {"id" data-point-id}}]}
           (call-resolver-with-local-conn
            "Query"
            "getDataPoint"
            ["id" "breakdowns/id" "breakdowns/parent/id"]
            {"id" data-point-id})))))
