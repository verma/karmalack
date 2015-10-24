(ns karmalack.routes
  (:require [karmalack.state :as state]
            [secretary.core :as secretary :refer-macros [defroute]]))

(defroute home "/" []
  (state/set-view! :home))



