(ns karmalack.runtime
  (:require [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.reload :refer [wrap-reload]]))

(def dev? true)

(defn wrap-handler [handler]
  (-> handler
      (wrap-reload)
      (wrap-cors :access-control-allow-origin #".*"
                 :access-control-allow-methods [:get])))


