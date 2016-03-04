(ns notifications-server.core
  (:require
   [clojure.core.async :as a]
   [clojure.stacktrace :refer [print-stack-trace]]
   [clojure.pprint :refer [pprint]]
   [compojure.core :as compojure :refer [GET POST]]
   [compojure.route :as route]
   [ring.middleware.params :as params]
   [ring.middleware.stacktrace :as trace]
   [aleph.http :as http]
   [byte-streams :as bs]
   [manifold.stream :as s]
   [manifold.deferred :as d]
   [manifold.bus :as bus]
   ;; Environment and configuration
   [environ.core :refer [env]]
   ;; Redis
   [taoensso.carmine :as r]
   [taoensso.carmine.message-queue :as mq]))


(def non-websocket-request
  {:status 400
   :headers {"content-type" "application/text"}
   :body "Expected a websocket request."})


(defn echo-handler [req]
  (-> (http/websocket-connection req)
      (d/chain
       (fn [socket]
         (s/connect socket socket)))
      (d/catch
          (fn [_]
            non-websocket-request))))


(def channels (bus/event-bus))

(defn subscription-handler [req]
  (d/let-flow [conn (d/catch
                        (http/websocket-connection req)
                        (fn [_] nil))]
    (if-not conn
      non-websocket-request
      (d/let-flow [channel-id (s/take! conn)]
        (s/connect
         (bus/subscribe channels channel-id)
         conn)))))

(defn notification-handler [req]
  (let [params (:params req)
        id (params "id")
        message (params "message")]
    (pprint (bus/topic->subscribers channels))
    (if (bus/active? channels id)
      (let [result (bus/publish! channels id message)]
        (if (and @result (bus/active? channels id))
          {:status 200
           :headers {"content-type" "application/text"}
           :body "Ok"}
          {:status 200
           :headers {"content-type" "application/text"}
           :body "Not delivered"}))
      {:status 200
       :headers {"content-type" "application/text"}
       :body "No subscribers"})))

(def app
  (params/wrap-params
   (compojure/routes
    (GET "/echo" [] (if (env :production)
                      {:status 404
                       :headers {"Content-Type" "text/plain"}
                       :body "404 Not Found."}
                      echo-handler))
    (GET "/subscribe" [] subscription-handler)
    (POST "/notify" [] notification-handler)
    (route/not-found "What are you trying to do?"))))


(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    (.close @server)))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 10000))]
    (reset! server (http/start-server app {:port port}))))
