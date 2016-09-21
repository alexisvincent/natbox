(ns natbox.core.client
  (:require [natbox.networking.tcp :as tcp]
            [manifold.stream :as s]
            aleph.tcp
            [natbox.core.util :as util]
            [clojure.core.match :refer [match]]
            [natbox.networking.ip :as ip]
            [natbox.networking.comms :as comms]))

(declare init start stop prompt make-stream worker)

(defn init [int-or-ext server-ip server-port]
  (atom {:kind        "client"
         :internal    (= int-or-ext "internal")
         :server/ip   server-ip
         :server/port (read-string server-port)
         :mac         (ip/random-mac)}))

(defn write [client msg]
  (util/write (:stream @client) msg))

(defn start [this]
  ; Create stream to server and construct a function to write from the stream
  (let [stream (make-stream this (:server/ip @this) (:server/port @this))
        worker (worker this 10000)]

    ;; Assign to atom the stream and write function
    (swap! this #(assoc %
                  :stream stream
                  :worker worker))

    ;; Request an ip address
    (let [mac (:mac @this)]
      (if (:internal @this)
        (write this (comms/request-ip mac))
        (let [ip (ip/random-external-ip)]
          (swap! this #(assoc % :ip ip))
          (println "\n :: Client : Connected to Natbox as external client " ip)
          (write this (comms/inform-external-ip mac ip))))))

  ;; Prompt user for input
  ((util/prompt (partial prompt this) "client >> ")))

(defn stop [this]
  (future-cancel (:worker @this))
  (s/close! (:stream @this)))

(defn msg-handler [client stream msg]
  (match (:label msg)
         'assign-ip
         (let [ip (get-in msg [:payload :ip])]
           ;; associate with the client atom it's ip
           (swap! client #(assoc % :ip ip))
           (println "\n :: Client : Recieved Allocated IP " (:ip @client)))

         'packet
         (do
           (println "\n :: Client : Packet Recieved from " (get-in msg [:payload :src]) "\n\n" (:payload msg) "\n"))

         :else (println "Unmatched " msg)))


(defn worker [client delay]
  (future
    (while (not (Thread/interrupted))
      (Thread/sleep delay)
      (write client (comms/heartbeat (:mac @client))))))


(defn make-stream [client server-ip server-port]
  "Create a new "
  (let [stream @(aleph.tcp/client {:host server-ip :port server-port})]
    (util/consume-edn-stream stream (partial msg-handler client stream))
    stream))

(defn prompt-msg [client]
  ((util/prompt
     (fn [dest-ip]
       ((util/prompt
          (fn [dest-port]
            ((util/prompt
               (fn [msg]
                 (write client
                        (comms/packet
                          (:mac @client)
                          (tcp/packet (:ip @client) (rand-int 80000) dest-ip (read-string dest-port) msg)))
                 false)
               "msg >> "))
            false)
          "msg > port >> "))
       false)
     "msg > ip >> "))
  true)


(defn prompt [client input]
  "Prompt for and handle user input"
  (match input
         "exit" false
         "quit" false

         "msg" (prompt-msg client)

         :else true))
