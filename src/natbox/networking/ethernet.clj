(ns natbox.networking.ethernet)

(defn frame [proto src dest payload]
  (let [header
        {;frame preamble
         :preamble   0
         :sfd        nil
         :dest-mac   nil
         :src-mac    nil
         :fcf        nil
         :ether-type :ether
         :crc        nil
         ; The data payload that needs to be delivered
         :payload    payload}]

    header))

