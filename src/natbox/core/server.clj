(ns natbox.core.server
  (:require [aleph.tcp :as tcp]
            [clojure.core.match :refer [match]]
            [natbox.core.util :as util]
            clojure.main
            [natbox.networking.ip :as ip]
            [natbox.core.client :as client]
            [natbox.networking.comms :as comms]))

(declare init start stop prompt-handler msg-handler update-port-table update-client-table)

(defn init [port network]
  "Initialise the server kernel object"
  (let [network (next (seq (ip/make-network network)))
        mac (ip/random-mac)]
    (atom {:kind           "server"
           :port           (read-string port)
           :mac            mac
           :network        network
           :natbox-ip      (first network)
           :assignable-ips (next network)
           :client-table   {mac {:internal true
                                 :natbox   true
                                 :mac      mac
                                 :ip       "10.0.0.1"}}
           :port-table     {}
           :halflife       10000})))


(defn worker [server delay]
  (future
    (while (not (Thread/interrupted))
      (Thread/sleep delay)
      (when (not (Thread/interrupted))
        ;(println "here")
        (swap!
          server
          (fn [current]
            (let [halflife (get current :halflife)]
              (update
                current :port-table
                (fn [port-table]
                  (into {}
                    (filter
                      (fn [[k v]]
                        (< (- (System/currentTimeMillis) (:time v)) halflife))
                      port-table)))))))))))


(defn start [this]
  ; Start the tcp server
  (let [tcp-server (tcp/start-server
                     (fn [stream info]
                       (util/consume-edn-stream
                         stream
                         (partial msg-handler this stream)))
                     {:port (:port @this)})

        worker (worker this 1000)]

    ; Atomically add the tcp server
    (swap! this
           #(assoc %
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

  ;((:lookup-ip @this) "123")

  ; Start the server prompt
  ((util/prompt (partial prompt-handler this) "server >> ")))

(defn stop [this]
  "Stop the server services"
  (future-cancel (:worker @this))
  (.close (:tcp-server @this)))

(defn packet-handler [server src-client packet]
  (let [dest-client ((:lookup-ip @server) (:dest packet))]

    (when (not (nil? dest-client))
      (if (:internal src-client)
        ;internal src
        (if (:internal dest-client)

          ; internal -> internal
          (do
            (println "\n :: Server : Forwarding packet [ " (:ip src-client) " -> " (:ip dest-client) "]\n\n")
            (util/write (:stream dest-client) (comms/packet (:mac @server) packet)))

          ; internal -> external
          (let [src-port (update-port-table server src-client (:port (:payload src-client)))
                updated-packet (-> packet
                                   (update-in [:payload] #(assoc % :src-port src-port))
                                   (assoc :src "0.0.0.0"))
                checksum (ip/calculate-checksum updated-packet)
                packet-with-checksum (assoc updated-packet :checksum checksum)]

            (println
              "\n :: Server : Forwarding packet "
              (:ip src-client) ":" (:src-port (:payload packet))
              " -> 0.0.0.0 :" (:src-port (:payload updated-packet))
              " ->" (get updated-packet :dest) ":" (get-in updated-packet [:payload :dest-port] "\n\n"))
            (util/write (:stream dest-client) (comms/packet (:mac @server) packet-with-checksum))))

        ;external src
        (when (and (:internal dest-client) (:natbox dest-client))
          ;external client sent packet to server/natbox
          (let [dest-port (get-in packet [:payload :dest-port])
                port-mapping (get-in @server [:port-table dest-port])]

            (when (not (nil? port-mapping))
              (let [updated-packet (-> packet
                                       (update-in [:payload] #(assoc % :dest-port (:src-port port-mapping)))
                                       (assoc :dest (:ip (:client-info port-mapping))))
                    checksum (ip/calculate-checksum updated-packet)
                    packet-with-checksum (assoc updated-packet :checksum checksum)]
                (util/write (get-in port-mapping [:client-info :stream]) (comms/packet (get @server :mac) packet-with-checksum))))))))))


(defn update-port-table [server client-info src-port]
  "Update the local port table to point to relevent client and return allocated port"
  (let [port (rand-int 80000)]
    (swap!
      server
      (fn [current]
        current
        (update
          current :port-table
          (fn [port-table]
            (assoc port-table
              port {:src-port src-port :port port :time (System/currentTimeMillis) :client-info client-info})))))
    port))



(defn update-client-table [mac server stream internal? ip-if-external]
  "Allocates an IP address to a mac address (taking into account current server state)"
  (swap! server
         (fn [current]
           (if internal?
             (-> current
                 ; Reduce assignable network
                 (update-in [:assignable-ips] next)
                 ; Update client table with new internal client info
                 (update-in [:client-table]
                            #(assoc % mac {;this is an internal client
                                           :internal true
                                           ; Ip being assigned to mac
                                           :ip       (ip/ip-address
                                                       (first
                                                         (:assignable-ips current)))
                                           :mac      mac
                                           ; The stream associated with this mac
                                           :stream   stream})))
             (-> current
                 ; Update client table with new external client info
                 (update-in [:client-table]
                            #(assoc % mac {; This is an external client
                                           :internal false
                                           :mac      mac
                                           :ip       ip-if-external
                                           :stream   stream}))))))



  (:client-table @server))

(defn msg-handler [server stream msg]
  "Handle packets comming in from clients"
  (let [mac (:mac msg)
        client-info ((:get-client-info @server) mac)]
    (match (:label msg)
           'request-ip
           (util/write stream (comms/assign-ip
                                (:mac @server)
                                (let [mac (get-in msg [:mac])]
                                  (-> mac
                                      (update-client-table server stream true '_)
                                      (get-in [mac :ip])))
                                (:mac @server)))

           'heartbeat
           (do)

           'inform-external-ip
           (update-client-table mac server stream false (get-in msg [:payload :ip]))

           'packet
           (packet-handler server client-info (:payload msg)))))


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

         "client" (do
                    (doto (client/init "internal" "localhost" "8000")
                      client/start
                      client/stop)
                    true)

         "info" (do
                  (println)
                  (prn)
                  (println)
                  true)

         "help" (do
                  (println "Usage: exit | repl | client | info | help")
                  true)

         :else true))
