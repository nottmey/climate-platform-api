(ns cdk.stack
  (:import (software.amazon.awscdk App Environment RemovalPolicy Stack StackProps)
           (software.amazon.awscdk.services.s3 Bucket$Builder)))

(defn synth [opts]
  (let [app   (App.)
        props (-> (StackProps/builder)
                  (.env (-> (Environment/builder)
                            (.account (System/getenv "CDK_DEFAULT_ACCOUNT"))
                            (.region (System/getenv "CDK_DEFAULT_REGION"))
                            (.build)))
                  (.description "Some description")
                  (.tags {"Type"        "Example"
                          "Application" "hello-cdk"})
                  (.build))
        stack (Stack. app "HelloCdkStack" props)]
    (-> (Bucket$Builder/create stack "MyFirstBucket")
        (.versioned true)
        (.removalPolicy RemovalPolicy/DESTROY)
        (.autoDeleteObjects true)
        (.build))
    (.synth app)))

