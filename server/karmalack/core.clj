(ns karmalack.core
  (:require [com.stuartsierra.component :as component]
            [karmalack.system :refer [make-system]]
            [karmalack.tools :as tools])
  (:gen-class))


(defn start-app []
  (let [system (component/start (make-system))]
    (fn []
      (component/stop system))))

(defn execute-command! [command args]
  (case command
    ":setup-db" (tools/setup-database!)
    (println "Unknown command:" command)))

(defn -main [& args]
  (if-let [cmd (first args)]
    (execute-command! cmd (rest args))
    (start-app)))
