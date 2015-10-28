(defproject karmalack "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.122"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.omcljs/om "0.9.0"]
                 [secretary "1.2.3"]
                 [cljs-http "0.1.37"]
                 [sablono "0.3.6"]
                 [compojure "1.4.0"]
                 [liberator "0.13"]
                 [org.clojure/java.jdbc "0.4.2"]
                 [com.stuartsierra/component "0.3.0"]
                 [org.julienxx/clj-slack "0.5.1"]
                 [environ "1.0.1"]
                 [org.postgresql/postgresql "9.4-1204-jdbc41"]
                 [yesql "0.5.1"]
                 [clj-time "0.11.0"]
                 [com.datomic/datomic-free "0.9.5153"]
                 [slack-rtm "0.1.0"]
                 [ring-cors "0.1.7"]
                 [ring/ring-defaults "0.1.2"]
                 [ring/ring-devel "1.4.0"]]

  :plugins [[lein-cljsbuild "1.1.0"]
            [lein-figwheel "0.4.1"]]

  :profiles {:dev
             {:datomic {:config "resources/free-transactor-template.properties"}}}

  :source-paths ["src" "server"]
  :main karmalack.core

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :cljsbuild {
    :builds [{:id "dev"
              :source-paths ["src"]

              :figwheel { :on-jsload "karmalack.core/on-js-reload" }

              :compiler {:main karmalack.core
                         :asset-path "js/compiled/out"
                         :output-to "resources/public/js/compiled/karmalack.js"
                         :output-dir "resources/public/js/compiled/out"
                         :source-map-timestamp true }}
             {:id "min"
              :source-paths ["src"]
              :compiler {:output-to "resources/public/js/compiled/karmalack.js"
                         :main karmalack.core
                         :optimizations :advanced
                         :pretty-print false}}]}

  :figwheel {
             ;; :http-server-root "public" ;; default and assumes "resources" 
             ;; :server-port 3449 ;; default
             ;; :server-ip "127.0.0.1" 

             :css-dirs ["resources/public/css"] ;; watch and update CSS

             ;; Start an nREPL server into the running figwheel process
             ;; :nrepl-port 7888

             ;; Server Ring Handler (optional)
             ;; if you want to embed a ring handler into the figwheel http-kit
             ;; server, this is for simple ring servers, if this
             ;; doesn't work for you just run your own server :)
             :ring-handler karmalack.server/app

             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"

             ;; if you want to disable the REPL
             ;; :repl false

             ;; to configure a different figwheel logfile path
             ;; :server-logfile "tmp/logs/figwheel-logfile.log" 
             })
