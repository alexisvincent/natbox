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
         :internal?  (= int-or-ext "internal")
         :server/ip   server-ip
         :server/port (read-string server-port)
         :mac         (ip/random-mac)}))


(defn start [this]
  ; Create stream to server and construct a function to write from the stream
  (let [stream (make-stream this (:server/ip @this) (:server/port @this))
        write (util/write stream)
        update (fn [updater]
                 (swap! this updater))
        worker (worker this 1000)]

    ;; Assign to atom the stream and write function

    (update #(assoc %
                :stream stream
                :write write
                :worker worker
                :update update))

    ;; Request an ip address
    (let [mac (:mac @this)]
      (if (:internal? @this)
        ((:write @this) (comms/request-ip mac))
        (let [ip (ip/random-external-ip)]
          (update #(assoc % :ip ip))
          ((:write @this) (comms/inform-external-ip mac ip))))))

  ;; Prompt user for input
  ((util/prompt (prompt this) "client >> ")))

(defn stop [this]
  (future-cancel (:worker @this))
  (s/close! (:stream @this)))

(defn msg-handler [client stream msg]
  (match (:label msg)
         'assign-ip
         (let [ip (get-in msg [:payload :ip])]
           ;; associate with the client atom it's ip
           (:update client) #(assoc % :ip ip)
           (println ":: Client aquired " ip))))


(defn worker [client delay]
  (future
    (while (not (Thread/interrupted))
      (Thread/sleep delay)
      ((:write @client) (comms/heartbeat (:mac @client))))))


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
                 ((:write @client) (comms/packet
                                     (:mac @client)
                                     (tcp/packet (:ip @client) "source port" dest-ip dest-port msg)))
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
