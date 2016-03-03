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
   [environ.core :refer [env]]))


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
  (let [id ((:params req) "id")
        message ((:params req) "message")]
    (if (bus/active? channels id)
      (do (bus/publish! channels id message)
          {:status 200
           :headers {"content-type" "application/text"}
           :body "Ok"})
      {:status 200
       :headers {"content-type" "application/text"}
       :body "Nope"})))

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
    (route/not-found "No such page."))))


;; (defn- wrap-with-error-handler [app]
;;   "Ring wrapper providing exception capture"
;;   (let [wrap-error-page
;;         (fn [handler]
;;           (fn [req]
;;             (try (handler req)
;;                  (catch Exception e
;;                    (try (print-stack-trace e 20)
;;                         (catch Exception g
;;                           (println "Exception trying to log exception?")))
;;                    {:status 500
;;                     :headers {"Content-Type" "text/plain"}
;;                     :body "500 Internal Error in Pro Server."}))))]
;;     ((if (or (env :production)
;;              (env :staging))
;;        wrap-error-page
;;        trace/wrap-stacktrace)
;;      app)))

(defonce server (atom nil))

;; (defn stop-server []
;;   (when-not (nil? @server)
;;     ;; graceful shutdown: wait 100ms for existing requests to be finished
;;     (@server :timeout 100)
;;     (reset! server nil)))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 10000))]
    (reset! server (http/start-server app {:port port}))))
