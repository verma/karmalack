(ns karmalack.config
  (:require [environ.core :refer [env]]))

(defn config []
  (let [file (or (:config-file env)
                 "config.edn")]
    (clojure.edn/read-string (slurp file))))

(defn slack-conn-config []
  (let [c (config)]
    {:api-url "https://slack.com/api"
     :token (:slack-api-token c)}))
