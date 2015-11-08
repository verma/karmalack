(ns karmalack.system
 (:require [com.stuartsierra.component :as component]
           [figwheel-sidecar.auto-builder :as fig-auto]
           [figwheel-sidecar.core :as fig]
           [karmalack.server :refer [new-server]]
           [karmalack.db :refer [new-database]]
           [karmalack.slack :refer [new-slackbot]]
           [karmalack.slack-api :refer [new-slack-api]]
           [karmalack.config :refer [config]]))


(defrecord Figwheel [figwheel-server]
 component/Lifecycle
 (start [this]
  (if figwheel-server
   this
   (let [server (fig/start-server {:css-dirs ["resources/public/css"]})
         config {:builds          [{:id           "dev"
                                    :source-paths ["src"]
                                    :figwheel     {:on-jsload "karmalack.core/on-js-reload"}
                                    :compiler     {:main                 'karmalack.core
                                                   :asset-path           "js/compiled/out"
                                                   :output-to            "resources/public/js/compiled/karmalack.js"
                                                   :output-dir           "resources/public/js/compiled/out"
                                                   :foreign-libs [{:file "src/vendor/chartist.js"
                                                                   :provides ["cljsjs.chartist"]}]
                                                   :source-map           true
                                                   :optimizations        :none
                                                   :source-map-timestamp true}}]
                 :figwheel-server server}]
    (println ":: fw :: startup")
    (fig-auto/autobuild* config)
    (assoc this :figwheel-server server))))

 (stop [this]
  (if figwheel-server
   (do
    (println ":: fw :: shutdown")
    (.stop figwheel-server)
    (assoc this :figwheel-server nil))
   this)))

(defn new-figwheel-server [config]
 (map->Figwheel config))


(defn make-system []
  (println "-- starting dev system.")
  (let [c (config)]
    (component/system-map
      :database (new-database c)
      :slack-api (new-slack-api c)
      :figwheel (new-figwheel-server c)
      :server (component/using
                (new-server c)
                [:database :slack-api])
      :skackbot (component/using
                  (new-slackbot c)
                  [:database :slack-api]))))
