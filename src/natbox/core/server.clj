(ns natbox.core.server
  (:require [aleph.tcp :as tcp]
            [manifold.stream :as s]
            [taoensso.nippy :as nippy]
            [clojure.core.match :refer [match]]
            [natbox.core.util :as util]
            [clojure.string :as str]
            clojure.main
            [natbox.core.client :as client]))

(declare init start stop prompt-handler msg-handler)

(defn init [port & args]
  "Initialise the server kernel object"
  (atom {:kind "server" :port (read-string port)}))

(defn start [this]
  ; Start the tcp server
  (let [tcp-server (tcp/start-server
                     (util/stream-handler (msg-handler this))
                     {:port (:port @this)})]

    ; Atomically add the tcp server
    (swap! this (fn [current]
                  (assoc current
                    :tcp-server tcp-server))))

  ; Start the server prompt
  ((util/prompt (partial prompt-handler this) "server >> ")))

(defn stop [this]
  "Stop the server services"
  (.close (:tcp-server @this)))

(defn msg-handler [server]
  (fn [stream msg]
    "Handle packets comming in from clients"))
    ;(doseq [x [1 2 3 4 5 6 7 8 9 10]]
    ;  (Thread/sleep 1000)
    ;  ((util/write stream) {:message "Message" x " from the server"}))))

(defn prompt-handler [server input]
  "The server prompt"
  (match input
         "exit" false

         "repl" (let []
                  ((util/prompt (fn [input]
                                 (if (not (= input "exit"))
                                   (let []
                                     (prn (eval (read-string input)))
                                     true)
                                   false)) "repl >> "))
                  true)

         "client" (let [client (client/init "localhost" "8000")]
                    (client/start client)
                    (client/stop client)
                    true)

         "info" (let []
                  (println)
                  (prn server)
                  (println)
                  true)

         "help" (println "Usage: exit | repl | client | info | help")

         :else true))
