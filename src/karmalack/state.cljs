(ns karmalack.state
  (:require [om.core :as om]
            [cljs-http.client :as http]
            [cljs.core.async :as async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

;; app state
(defonce app-state (atom {:all-users []
                          :current-user {}
                          :current-view :home}))

;; root cursor into our state
(def root-cursor (om/root-cursor app-state))

;; overserable views into app state
(def root (om/ref-cursor root-cursor))
(def all-users (om/ref-cursor (:all-users root)))
(def current-user (om/ref-cursor (:current-user root)))

(declare load-users!)
(declare load-user-info!)

(defn set-view!
  "Change the application view to the specified one"
  ([view]
    (set-view! view {}))
  ([view params]
   (om/transact! root-cursor #(assoc % :current-view view
                                       :current-view-params params))
   (case view
     :home
     (load-users!)
     :user
     (load-user-info! (:id params)))))

(defn- url [& parts]
  (apply str
         "http://localhost:3000/api/"
         (clojure.string/join "/" parts)))

(defn load-users!
  "Load all users from our API server"
  []
  (go (let [r (-> (url "users")
                  (http/get {:with-credentials? false})
                  <!
                  :body :users)]
        (om/update! all-users r)
        r)))

(defn load-user-info!
  "Load info for the given user"
  [id]
  (go (let [r (-> (url "users" id)
                  (http/get {:with-credentials? false})
                  <!
                  :body)]
        (println "-- -- loaded:" r)
        (om/update! current-user r)
        r)))
