(ns ^:figwheel-always karmalack.core
  (:require [karmalack.views :as views]
            [karmalack.routes :refer [start-routes!]]
            [om.core :as om]
            [secretary.core :as secretary :include-macros true :refer-macros [defroute]]
            [sablono.core :as html :refer-macros [html]]
            [karmalack.state :as state]))

(enable-console-print!)

;; define your app data so that it doesn't get over-written on reload
(defn main-app [_ owner]
  (reify
    om/IRender
    (render [_]
      (let [r (om/observe owner state/root)
            view (:current-view @r)]
        (html
          [:div.main-app
           ;; depending upon our view show stuff
           ;;
           (case view
             :home (om/build views/home {})
             :user (om/build views/user-view {}))])))))

(defn startup []
  (om/root main-app state/app-state
           {:target (.getElementById js/document "app")})
  (start-routes!))

(startup)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)

