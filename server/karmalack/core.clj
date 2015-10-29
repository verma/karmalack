(ns karmalack.core
  (:require [com.stuartsierra.component :as component]
            [karmalack.server :refer [new-server]]
            [karmalack.db :refer [new-database]]
            [karmalack.slack :refer [new-slackbot]]
            [karmalack.figwheel :refer [new-figwheel-server]]
            [karmalack.config :refer [config dev?]])
  (:gen-class))


(defn prod-app-system []
  (let [c (config)]
    (component/system-map
      :database (new-database c)
      :server (component/using
                (new-server c)
                [:database])
      :skackbot (component/using
                  (new-slackbot c)
                  [:database]))))

(defn dev-app-system []
  (let [c (config)]
    (component/system-map
      :database (new-database c)
      :figwheel (new-figwheel-server c)
      :server (component/using
                (new-server c)
                [:database])
      :skackbot (component/using
                  (new-slackbot c)
                  [:database]))))

(defn start-app []
  (let [system (component/start (if (dev?)
                                  (dev-app-system)
                                  (prod-app-system)))]
    (fn []
      (component/stop system))))

(defn -main [& args]
  (start-app))
