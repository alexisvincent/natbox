(ns natbox.core.server
  (:require [aleph.tcp :as tcp]
            [manifold.stream :as s]
            [taoensso.nippy :as nippy]
            [clojure.core.match :refer [match]]
            [natbox.core.util :as util]
            [clojure.string :as str]
            clojure.main
            [natbox.networking.ip :as ip]
            [natbox.core.client :as client]
            [natbox.networking.comms :as comms]))

(declare init start stop prompt-handler make-msg-handler update-client-table)

(defn init [port network]
  "Initialise the server kernel object"
  (let [network (next (seq (ip/make-network network)))]
    (atom {:kind           "server"
           :port           (read-string port)
           :mac            (ip/random-mac)
           :network        network
           :natbox-ip      (first network)
           :assignable-ips (next network)
           :client-table   {}})))

(defn worker [server delay]
  (future
    (while (not (Thread/interrupted))
      (Thread/sleep delay))))

(defn start [this]
  ; Start the tcp server
  (let [tcp-server (tcp/start-server
                     (fn [stream info]
                       (util/consume-edn-stream
                         stream
                         (make-msg-handler this stream)))
                     {:port (:port @this)})

        update (fn [updater]
                 (swap! this updater))

        worker (worker this 1000)]

    ; Atomically add the tcp server
    (update #(assoc %
              :update update
              :worker worker
              :get-client-info (fn [mac] (get-in @this [:client-table mac]))
              :lookup-ip (fn [ip]
                           (->> (vals (:client-table @this))
                                (filter
                                  (fn [client-info]
                                    (when (= ip (:ip client-info))
                                      client-info)))
                                (first)))


              :tcp-server tcp-server)))

  ((:lookup-ip @this) "123")

  ; Start the server prompt
  ((util/prompt (partial prompt-handler this) "server >> ")))

(defn stop [this]
  "Stop the server services"
  (future-cancel (:worker @this))
  (.close (:tcp-server @this)))

(defn packet-handler [server client-info packet]
  (let [dest-client ((:lookup-ip @server) (:dest packet))]

    (when (not (nil? dest-client))
      (if (:internal? client-info)
        (do
          ; Packet from internal client
          (println "Forwarding packet"))
        (do
          ; Packet from external client
          (println "Got packet from external"))))

    (println "Packet FROM " (:mac client-info) " " (:ip client-info))))

(defn make-msg-handler [server stream]
  "Construct a message handler from an instance of a server"
  (fn [msg]
    "Handle packets comming in from clients"
    (let [mac (:mac msg)
          client-info ((:get-client-info @server) mac)]
      (match (:label msg)
             'request-ip
             ((util/write stream) (comms/assign-ip
                                    (:mac @server)
                                    (let [mac (get-in msg [:mac])]
                                      (-> mac
                                          (update-client-table server stream true '_)
                                          (get-in [mac :ip])))
                                    (:mac @server)))

             'heartbeat
             (do)

             'inform-external-ip
             (println "External")

             'packet
             (packet-handler server client-info (:payload msg))))))

(defn update-client-table [mac server stream internal? ip-if-external]
  "Allocates an IP address to a mac address (taking into account current server state)"
  ((:update @server) (fn [current]
                       (if internal?
                         (-> current
                             ; Reduce assignable network
                             (update-in [:assignable-ips] next)
                             ; Update client table with new internal client info
                             (update-in [:client-table]
                                        #(assoc % mac {;this is an internal client
                                                       :internal? true
                                                       ; Ip being assigned to mac
                                                       :ip        (ip/ip-address
                                                                    (first
                                                                      (:assignable-ips current)))
                                                       :mac mac
                                                       ; The stream associated with this mac
                                                       :stream    stream})))
                         (-> current
                             ; Update client table with new external client info
                             (update-in [:client-table]
                                        #(assoc % mac {; This is an external client
                                                       :internal? false
                                                       :mac mac
                                                       :ip        ip-if-external
                                                       :stream    stream}))))))



  (:client-table @server))

(defn prompt-handler [server input]
  "The server prompt"
  (match input

         "exit" false
         "q" false

         "repl" (let []
                  ((util/prompt (fn [input]
                                  (if (not (= input "exit"))
                                    (let []
                                      (->> input
                                           (read-string)
                                           (eval)
                                           (prn))
                                      true)
                                    false)) "repl >> "))
                  true)

         "client" (doto (client/init "internal" "localhost" "8000")
                    (client/start)
                    (client/stop)
                    true)

         "info" (let []
                  (println)
                  (prn)
                  (println)
                  true)

         "help" (println "Usage: exit | repl | client | info | help")

         :else true))
