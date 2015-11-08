(ns karmalack.state
  (:require [om.core :as om]
            [cljs-http.client :as http]
            [cljs.core.async :as async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

;; app state
(defonce app-state (atom {:all-users []
                          :loader {}
                          :current-user {}
                          :team {}
                          :current-view :home}))

;; root cursor into our state
(def root-cursor (om/root-cursor app-state))

;; overserable views into app state
(def root (om/ref-cursor root-cursor))
(def all-users (om/ref-cursor (:all-users root)))
(def current-user (om/ref-cursor (:current-user root)))
(def team (om/ref-cursor (:team root)))
(def loader (om/ref-cursor (:loader root)))

(declare load-users!)
(declare load-team-info!)
(declare load-user-info!)

(defn- view-load-actions! [view params]
  (.scrollTo js/window 0 0)
  (case view
    :home (load-users!)
    :user (load-user-info! (:id params))))

(defn set-view!
  "Change the application view to the specified one"
  ([view]
    (set-view! view {}))
  ([view params]
   (view-load-actions! view params)
   (om/transact! root-cursor #(assoc % :current-view view
                                       :current-view-params params))))

(defn- url [& parts]
  (apply str
         "/api/"
         (clojure.string/join "/" parts)))

(defn- set-loading [msg]
  (if (clojure.string/blank? msg)
    (om/update! loader {})
    (om/update! loader {:message msg})))

(defn load-users!
  "Load all users from our API server"
  []
  (go (set-loading "Loading users...")
      (let [r (-> (url "users")
                  (http/get {:with-credentials? false})
                  <!
                  :body :users)]
        (om/update! all-users r)
        (set-loading nil)
        r)))

(defn load-team-info!
  "Load information about the team"
  []
  (go (set-loading "Loading team information...")
      (let [r (-> (url "team")
                  (http/get {:with-credentials? false})
                  <!
                  :body :team)]
        (om/update! team r)
        (set-loading nil)
        r)))

(defn load-user-info!
  "Load info for the given user"
  [id]
  (go (set-loading "Loading user information...")
      (om/update! current-user {})
      (let [r (-> (url "users" id)
                  (http/get {:with-credentials? false})
                  <!
                  :body)]
        (om/update! current-user r)
        (set-loading nil)
        r)))
