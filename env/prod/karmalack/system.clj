(ns karmalack.system
  (:require [com.stuartsierra.component :as component]
            [karmalack.server :refer [new-server]]
            [karmalack.db :refer [new-database]]
            [karmalack.slack :refer [new-slackbot]]
            [karmalack.slack-api :refer [new-slack-api]]
            [karmalack.config :refer [config]]))

(defn make-system []
  (println "-- starting production system.")
  (let [c (config)]
    (component/system-map
      :database (new-database c)
      :slack-api (new-slack-api c)
      :server (component/using
                (new-server c)
                [:database :slack-api])
      :skackbot (component/using
                  (new-slackbot c)
                  [:database :slack-api]))))
