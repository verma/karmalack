(ns karmalack.server
 (:require [karmalack.config :refer [slack-conn-config]]
           [karmalack.db :as db]
           [compojure.core :refer :all]
           [compojure.route :as route]
           [liberator.core :refer [defresource resource]]
           [clj-slack.core :as slack]
           [clj-slack.users :as users]
           [ring.middleware.cors :refer [wrap-cors]]
           [ring.middleware.reload :refer [wrap-reload]]
           [ring.middleware.defaults :refer [wrap-defaults api-defaults]]))


(defresource get-users []
  :available-media-types ["application/json"]
  :allowed-methods [:get]
  :handle-ok (fn [ctx]
               (let [res (slack/slack-request
                           (slack-conn-config)
                           "users.list" {"presence" "1"})
                     karma-stats (db/karma-stats)
                     settings (db/all-user-settings)]
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

(db/karma-stats)

(defroutes app-routes
  (context "/api" []
    (ANY "/users" [] (get-users))))

(def app
 (-> app-routes
     (wrap-defaults api-defaults)
     (wrap-cors :access-control-allow-origin [#".*"]
                :access-control-allow-methods [:get :put :post])
     (wrap-reload)))
