(defproject notifications-server "0.1.0-SNAPSHOT"
  :description "Async notifications server"
  :url "http://lantern.org"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [aleph "0.4.1-beta4"]
                 [manifold "0.1.2"]
                 [gloss "0.2.5"]
                 [compojure "1.3.3"]
                 [org.clojure/core.async "0.2.374"]
                 ;; Environment settings
                 [environ "1.0.0"]]
  :profiles {:uberjar {:main pro-server.web
                       :aot :all}
             :production {:env {:production true
                                :log true}}
             :staging {:env {:staging true
                             :log true}}
             :dev {:env {:log true
                         :testing true}
                   :dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [midje "1.6.3"]]
                   :plugins [[cider/cider-nrepl "0.11.0-SNAPSHOT"]
                             [lein-midje "3.1.3"]]}
             :test {:env {:log true
                          :testing true}}}
  :uberjar-name "notifications-server-standalone.jar"
  :main notifications-server.core)
