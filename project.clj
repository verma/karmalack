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

                 [http-kit "2.1.18"]
                 [compojure "1.4.0"]
                 [liberator "0.13"]
                 [com.stuartsierra/component "0.3.0"]
                 [org.julienxx/clj-slack "0.5.1"]
                 [environ "1.0.1"]
                 [clj-time "0.11.0"]
                 [com.datomic/datomic-free "0.9.5153" :exclusions [joda-time]]
                 [slack-rtm "0.1.0"]
                 [org.clojure/core.cache "0.6.4"]
                 [ring-cors "0.1.7"]
                 [ring/ring-defaults "0.1.2"]
                 [ring/ring-devel "1.4.0"]]

  :plugins [[lein-cljsbuild "1.1.0"]
            [lein-figwheel "0.4.1"]]

  :source-paths ["src" "server"]

  :profiles {:dev
             {:dependencies [[figwheel-sidecar "0.4.1"]]
              :env {:dev true}
              :source-paths ["env/dev"]
              :main karmalack.core
              :datomic {:config "resources/free-transactor-template.properties"}}

             :uberjar
             {:aot :all
              :hooks [leiningen.cljsbuild]
              :source-paths ["env/prod"]
              :main karmalack.core
              :cljsbuild {:builds [{:id "min"
                                    :source-paths ["src"]
                                    :compiler {:output-to "resources/public/js/compiled/karmalack.js"
                                               :main karmalack.core
                                               :optimizations :advanced
                                               :foreign-libs [{:file "vendor/chartist.min.js"
                                                               :provides ["cljsjs.chartist"]}]
                                               :externs ["vendor/externs/chartist.js"]
                                               :pretty-print false}}]}}}


  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"])
