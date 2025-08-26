(ns server.core
  (:require [ring.adapter.jetty :as jetty]
            [ring.util.response :as response]))

(defn handler [_]
  (-> (response/resource-response "public/index.html")
      (response/content-type "text/html")))

(defn -main [& _]
  (jetty/run-jetty handler {:port 3000 :join? false}))
