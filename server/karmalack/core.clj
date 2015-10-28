(ns karmalack.core
  (:require [com.stuartsierra.component :as component]
            [karmalack.db :refer [new-database]]
            [karmalack.slack :refer [new-slackbot]]
            [karmalack.config :refer [config]])
  (:gen-class))


(defn app-system []
  (let [c (config)]
    (component/system-map
      :database (new-database c)
      :skackbot (component/using
                  (new-slackbot c)
                  [:database]))))

(defn start-app []
  (let [system (component/start (app-system))]
    (fn []
      (component/stop system))))

(defn -main [& args]
  (start-app))
