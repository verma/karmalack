(ns karmalack.server
 (:require [karmalack.config :refer [slack-conn-config dev?]]
           [karmalack.db :as db]
           [compojure.core :refer :all]
           [liberator.core :refer [defresource resource]]
           [clj-slack.core :as slack]
           [org.httpkit.server :refer [run-server]]
           [com.stuartsierra.component :as component]
           [ring.middleware.cors :refer [wrap-cors]]
           [ring.middleware.reload :refer [wrap-reload]]
           [ring.middleware.defaults :refer [wrap-defaults api-defaults]]))


(defresource get-users [database]
  :available-media-types ["application/json"]
  :allowed-methods [:get :options]
  :handle-ok (fn [ctx]
               (let [res (slack/slack-request
                           (slack-conn-config)
                           "users.list" {"presence" "1"})
                     karma-stats (db/karma-stats database)
                     settings (db/all-user-settings database)]
                 (when (:ok res)
                   {:users
                    (mapv
                      (fn [e]
                        (-> (select-keys e [:id :name :presence])
                            (merge (when-let [stats (get karma-stats (:id e))]
                                     {:karma stats}))
                            (merge (when-let [s (get settings (:id e))]
                                     {:settings s}))
                            (assoc :image (get-in e [:profile :image_48]))))
                      (:members res))}))))

(defn wrap-debug [handler]
  (-> handler
      (wrap-reload)
      (wrap-cors :access-control-allow-origin #".*"
                 :access-control-allow-methods [:get])))

(defrecord Server [port debug? database stop-fn]
  component/Lifecycle
  (start [this]
    (if stop-fn
      this
      (let [handler (-> (routes
                          (context "/api" []
                            (ANY "/users" [] (get-users database))))
                        (wrap-defaults api-defaults))
            port (or port 3000)
            stop (run-server (if (dev?)
                               (wrap-debug handler)
                               handler)
                             {:port port})]
        (println ":: server :: startup on port:" port "debug?" (dev?))
        (assoc this :stop-fn stop))))

  (stop [this]
    (if stop-fn
      (do
        (println ":: server :: shutdown.")
        (stop-fn)
        (assoc this :stop-fn nil))
      this)))

(defn new-server [config]
  (map->Server config))

