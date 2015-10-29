(ns karmalack.state
  (:require [om.core :as om]
            [cljs-http.client :as http]
            [cljs.core.async :as async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

;; app state
(defonce app-state (atom {:all-users []
                          :current-view :home}))

;; root cursor into our state
(def root-cursor (om/root-cursor app-state))

;; overserable views into app state
(def root (om/ref-cursor root-cursor))
(def all-users (om/ref-cursor (:all-users root)))

(declare load-users!)

(defn set-view!
  "Change the application view to the specified one"
  [view]
  (om/transact! root-cursor #(assoc % :current-view view))

  (case view
    :home
    (load-users!)))


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
