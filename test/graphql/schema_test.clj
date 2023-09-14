(ns graphql.schema-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [graphql.parsing :as parsing]
            [graphql.schema :as schema]
            [utils.cloud-import :refer [local-conn]]))

(def golden-schema-file (io/resource "goldens/schema.graphql"))

(comment
  (schema/generate local-conn)

  (printf (schema/generate local-conn))
  ; re-gen golden snapshot
  (spit golden-schema-file (str (schema/generate local-conn))))

(deftest generate-schema-test
  (let [golden-snapshot  (slurp golden-schema-file)
        generated-schema (str (schema/generate local-conn))]
    (is (parsing/valid? generated-schema))
    (is (string? golden-snapshot))
    (is (= generated-schema golden-snapshot))))
