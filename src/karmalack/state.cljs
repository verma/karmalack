(ns karmalack.state
  (:require [om.core :as om]))

;; app state
(defonce app-state (atom {:all-users []
                          :current-view :home}))

;; root cursor into our state
(def root-cursor (om/root-cursor app-state))

;; overserable views into app state
(def root (om/ref-cursor root-cursor))
(def all-users (om/ref-cursor (:all-users root)))

(defn set-view!
  "Change the application view to the specified one"
  [view]
  (om/transact! root-cursor #(assoc % :current-view view)))
