(ns karmalack.tools
  (:require [karmalack.db :as db]
            [karmalack.config :refer [config]]))

(defn setup-database! []
  (let [uri (:datomic-uri (config))]
    (if (clojure.string/blank? uri)
      (println "No :datomic-uri present in config file.")
      (do
        (println "setting up database at:" uri)
        (db/setup-database! uri)
        (println "done.")))))
