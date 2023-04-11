(ns ai.gpt
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]))

(defn list-models [api-key]
  ; https://platform.openai.com/docs/models
  (-> (client/get "https://api.openai.com/v1/models"
                  {:headers {"Authorization" (str "Bearer " api-key)}})
      :body
      (json/read-str)
      (get "data")
      (->> (map #(get % "id")))))

(defn complete-text [prompt model max-tokens api-key]
  ; https://platform.openai.com/docs/api-reference/completions/create
  (let [url     "https://api.openai.com/v1/completions"
        headers {"Content-Type"  "application/json"
                 "Authorization" (str "Bearer " api-key)}
        data    {:model      model
                 :prompt     prompt
                 :max_tokens max-tokens}]
    (-> (client/post url {:headers headers
                          :body (json/write-str data)})
        :body
        (json/read-str)
        (get "choices")
        (first)
        (get "text"))))

(defn answer-chat [messages model api-key]
  ; https://platform.openai.com/docs/api-reference/chat/create
  (let [url     "https://api.openai.com/v1/chat/completions"
        headers {"Content-Type"  "application/json"
                 "Authorization" (str "Bearer " api-key)}
        data    {:model    model
                 :messages messages}]
    (-> (client/post url {:headers headers
                          :body (json/write-str data)})
        :body
        (json/read-str)
        (get "choices")
        (first)
        (get "message")
        (get "content"))))

(def api-key "...")

(comment
  (list-models api-key)

  (complete-text "How is \"How are you?\" pronounced in 1. French, 2. Spanish and 3. Japanese? Use english language to describe it." "text-davinci-003" 200 api-key)

  (answer-chat [{"role" "user"
                 "content" "Who are you?"}] "gpt-3.5-turbo" api-key))