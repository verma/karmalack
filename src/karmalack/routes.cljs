(ns karmalack.routes
  (:require [karmalack.state :as state]
            [secretary.core :as secretary :refer-macros [defroute]]
            [goog.events :as events]
            [goog.history.EventType :as EventType])
  (:import goog.History))

(defroute home "/" []
  (state/set-view! :home))

(defroute user "/user/:id" [id]
  (println "-- -- setting user view:" id)
  (state/set-view! :user {:id id}))

(defn start-routes! []
  (secretary/set-config! :prefix "#")
  (let [h (History.)]
    (goog.events/listen h EventType/NAVIGATE #(secretary/dispatch! (.-token %)))
    (doto h (.setEnabled true))))


