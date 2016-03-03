(ns notifications-server.core
  (:require
   [clojure.core.async :as a]
   [compojure.core :as compojure :refer [GET]]
   [ring.middleware.params :as params]
   [compojure.route :as route]
   [aleph.http :as http]
   [byte-streams :as bs]
   [manifold.stream :as s]
   [manifold.deferred :as d]
   [manifold.bus :as bus]
   ;; Environment and configuration
   [environ.core :refer [env]]))


(def non-websocket-request
  {:status 400
   :headers {"content-type" "application/text"}
   :body "Expected a websocket request."})


(defn echo-handler
  [req]
  (-> (http/websocket-connection req)
      (d/chain
       (fn [socket]
         (s/connect socket socket)))
      (d/catch
          (fn [_]
            non-websocket-request))))

(def handler
  (params/wrap-params
   (compojure/routes
    (GET "/echo" [] echo-handler)
    (route/not-found "No such page."))))


(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    (@server :timeout 100)
    (reset! server nil)))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 10000))]
    (reset! server (http/start-server handler {:port port}))))
