(ns karmalack.server
 (:require [karmalack.config :refer [slack-conn-config dev?]]
           [karmalack.db :as db]
           [karmalack.slack-api :as sa]
           [compojure.core :refer :all]
           [liberator.core :refer [defresource resource]]
           [clj-slack.core :as slack]
           [org.httpkit.server :refer [run-server]]
           [com.stuartsierra.component :as component]
           [ring.middleware.cors :refer [wrap-cors]]
           [ring.middleware.reload :refer [wrap-reload]]
           [ring.middleware.defaults :refer [wrap-defaults api-defaults]]))


(defresource get-users [database slack-api]
  :available-media-types ["application/json"]
  :allowed-methods [:get]
  :handle-ok (fn [_]
               (let [users (sa/get-all-users slack-api)
                     karma-stats (db/karma-stats database)
                     settings (db/all-user-settings database)]
                 {:users
                  (mapv
                    (fn [[id info]]
                      (-> info
                          (assoc :id id)
                          (merge (when-let [stats (get karma-stats id)]
                                   {:karma stats}))
                          (merge (when-let [s (get settings id)]
                                   {:settings s}))))

                    users)})))

(defresource user-info [database slack-api id]
  :available-media-types ["application/json"]
  :allowed-methods [:get]
  :handle-ok (fn [_]
               (let [ui (sa/get-all-users slack-api)
                     chans (sa/get-all-channels slack-api)
                     info (get ui id)
                     karma-info (-> (db/all-user-stats database id)
                                    (update :stats
                                            (fn [stats]
                                              (keep (fn [[chan by delta]]
                                                      (let [cname (get chans chan)
                                                            by (get-in ui [by :name])]
                                                        (when (and cname by)
                                                          [cname by delta])))
                                                    stats))))]
                 (merge info
                        karma-info))))

(defn wrap-debug [handler]
  (-> handler
      (wrap-reload)
      (wrap-cors :access-control-allow-origin #".*"
                 :access-control-allow-methods [:get])))

(defrecord Server [port debug? database slack-api stop-fn]
  component/Lifecycle
  (start [this]
    (if stop-fn
      this
      (let [handler (-> (routes
                          (context "/api" []
                            (ANY "/users" [] (get-users database slack-api))
                            (ANY "/users/:id" [id] (user-info database slack-api id))))
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

(comment
  (defn start-debug [config]
    (let [sys (component/system-map
                :database (db/new-database config)
                :slack-api (sa/new-slack-api config)
                :server (component/using
                          (new-server config)
                          [:database :slack-api]))]
      (component/start sys)))

  (def sys (start-debug (karmalack.config/config)))

  (component/stop sys))


