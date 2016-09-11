(ns natbox.core.client
  (:require [natbox.networking.tcp :as tcp]
            [manifold.stream :as s]
            aleph.tcp
            [natbox.core.util :as util]
            [clojure.core.match :refer [match]]))

(declare init start stop prompt make-stream)

(defn init [server-ip server-port]
    (atom {:kind        "client"
           :server-ip   server-ip
           :server-port (read-string server-port)}))

(defn start [this]
  (let [stream (make-stream (:server-ip @this) (:server-port @this))
        write (util/write stream)]
    (swap! this (fn [current]
                  (assoc current
                    :stream stream
                    :write write))))
  ((util/prompt (prompt this) "client >> ")))

(defn stop [kernel]
  (s/close! (:stream @kernel)))

(defn msg-handler [stream msg]
  (println "Client Recieved " msg))

(defn make-stream [server-ip server-port]
  "Create a new "
  (let [stream @(aleph.tcp/client {:host server-ip :port server-port})]
    ((util/stream-handler msg-handler) stream {:info "Some info object"})
    stream))

(defn prompt-msg [client]
  ((util/prompt
    (fn [dest-ip]
      ((util/prompt
        (fn [dest-port]
          ((util/prompt
            (fn [msg]
              ((:write @client) (tcp/packet "source ip" "source port" dest-ip dest-port msg))
              false)
            "msg >> "))
          false)
        "msg > port >> "))
      false)
    "msg > ip >> "))
  true)


(defn prompt [client]
  "Prompt for and handle user input"
  (fn [input]
    (match input
           "exit" false
           "quit" false

           "msg" (prompt-msg client)

           :else true)))
