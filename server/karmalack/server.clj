(ns karmalack.server
 (:require [karmalack.config :refer [slack-conn-config]]
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
                           "users.list" {"presence" "1"})]
                 (when (:ok res)
                   {:users
                    (mapv #(hash-map
                            :id (:id %)
                            :name (:name %)
                            :presence (:presence %)
                            :image (get-in % [:profile :image_48]))
                          (:members res))}))))

(defroutes app-routes
  (GET "/" [] "Hello World")
  (GET "/users" [] (get-users))
  (route/not-found "Not Found"))

(def app
 (-> app-routes
     (wrap-defaults api-defaults)
     (wrap-cors :access-control-allow-origin [#".*"]
                :access-control-allow-methods [:get :put :post])
     (wrap-reload)))
