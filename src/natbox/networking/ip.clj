(ns natbox.networking.ip
  (:require [taoensso.nippy :as nippy]
            [pandect.algo.sha1 :as pandect]
            [clojure.network.ip :as netip]))

(defn calculate-checksum [packet]
  (->> (dissoc packet :payload :checksum)
       (nippy/freeze)
       (pandect/sha1)))

(def make-network netip/make-network)
(def ip-address netip/ip-address)

(defn random-mac []
   (->> #(rand-int 256)
        (repeatedly)
        (take 6)
        (map #(Integer/toString % 16))))

(defn verify-checksum [packet]
  (=
    (calculate-checksum packet)
    (:checksum packet)))

(defn packet [proto src dest payload]
  (let [header
        {; 4 bits that contain the version, that specifies if it's
         ; an IPv4 or IPv6 packet
         :version 4
         ; 4 bits that contain the Internet Header Length, which
         ; is the length of the header in multiples of 4 bytes
         ; (e.g., 5 means 20 bytes)
         :ihl     ""
         ; 8 bits that contain the Type of Service, also referred
         ; to as Quality of Service (QoS), which describes what
         ; priority the packet should have
         :tos     0
         ; 16 bits that contain the length of the packet in bytes
         :length  0
         ; 16 bits that contain an identification tag to help
         ; reconstruct the packet from several fragments,
         :id      (rand-int 999999999)
         ; a flag that says whether the packet is allowed to be
         ; fragmented or not (DF: Don't fragment)
         :df      false
         ; a flag to state whether more fragments of a packet
         ; follow (MF: More Fragments)
         :mf      false
         ; 13 bits that contain the fragment offset, a field to
         ; identify position of fragment within original packet
         :fo      0
         ; 8 bits that contain the Time to live (TTL), which is the
         ; number of hops (router, computer or device along a network)
         ; the packet is allowed to pass before it dies (for example,
         ; a packet with a TTL of 16 will be allowed to go across 16
         ; routers to get to its destination before it is discarded)
         :ttl     8
         ; 8 bits that contain the protocol (TCP, UDP, ICMP, etc.)
         :proto   proto
         ; 32 bits that contain the source IP address,
         :src     src
         ; 32 bits that contain the destination address.
         :dest    dest
         ; The data payload that needs to be delivered
         :payload payload}

        checksum (calculate-checksum header)]
    ; 16 bits that contain the Header Checksum, a number used
    ; in error detection
    (assoc header :checksum checksum)))

(def external-ips (seq (make-network "123.123.123.123/24")))

(defn random-external-ip []
  (do
    (def external-ips (next external-ips))
    (ip-address (first external-ips))))


