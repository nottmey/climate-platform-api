(ns cdk.stack
  (:import (software.amazon.awscdk App RemovalPolicy Stack StackProps)
           (software.amazon.awscdk.services.s3 Bucket$Builder)))

(defn synth [opts]
  (let [app   (App.)
        props (-> (StackProps/builder)
                  (.build))
        stack (Stack. app "HelloCdkStack" props)]
    (->
      (Bucket$Builder/create stack "MyFirstBucket")
      (.versioned true)
      (.removalPolicy RemovalPolicy/DESTROY)
      (.autoDeleteObjects true)
      (.build))
    (.synth app)))

