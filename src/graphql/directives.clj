(ns graphql.directives)

(def user-access
  {:name      :aws_cognito_user_pools
   :arguments [{:name  :cognito_groups
                :value ["Users"]}]})

; are also user, so don't need to be part in the user access description
(def admin-access
  {:name      :aws_cognito_user_pools
   :arguments [{:name  :cognito_groups
                :value ["Admins"]}]})
