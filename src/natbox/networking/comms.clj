(ns natbox.networking.comms
  (:require [taoensso.nippy :as nippy]))

(defn internal-message [mac label payload]
  {:label label
   :mac mac
   :payload payload})

(defn request-ip [mac]
  (internal-message mac 'request-ip {}))

(defn assign-ip [mac ip natbox-mac]
  (internal-message mac 'assign-ip {:natbox-mac natbox-mac :ip ip}))

(defn heartbeat [mac]
  (internal-message mac 'heartbeat {}))

(defn packet [mac packet]
  (internal-message mac 'packet packet))

(defn inform-external-ip [mac ip]
  (internal-message mac 'inform-external-ip {:ip ip}))