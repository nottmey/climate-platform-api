(ns cdk.stack
  (:require
   [cdk.app-sync :as app-sync]
   [io.pedestal.log :as log])
  (:import
   (software.amazon.awscdk
    App
    Environment
    Stack
    StackProps)))

(defn synth [_opts]
  (let [app   (App.)
        props (-> (StackProps/builder)
                  (.env (-> (Environment/builder)
                            (.account (System/getenv "CDK_DEFAULT_ACCOUNT"))
                            (.region (System/getenv "CDK_DEFAULT_REGION"))
                            (.build)))
                  (.description "Delivering a dynamically generated AppSync gateway based on our datomic instance.")
                  (.tags {"Type"        "API"
                          "Application" "climate-platform"})
                  (.build))
        stack (Stack. app "climate-platform-api" props)]
    (log/info :message "Synthesizing api stack")
    (app-sync/app-sync stack)
    (.synth app)))

