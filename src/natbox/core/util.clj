(ns natbox.core.util
  (:require [manifold.stream :as s]
            [taoensso.nippy :as nippy]))

(defn prompt [handle prompt-str]
  "Returns a prompt that will invoke handle with the user input
   if handle returns truthy, the prompt will prompt again"
  (fn []
    "Prompt for and handle user input"
    (print prompt-str)
    (flush)
    (if (handle (read-line))
      ((prompt handle prompt-str))
      "Have a nice day!")))

(defn write [stream]
  "Accepts a valid stream and returns a function that accepts edn and writes that to the stream"
  (fn [msg]
    (try
      @(s/try-put! stream (nippy/freeze msg) 0)
      (catch Exception e false))))

(defn stream-handler [msg-handler]
  (fn [stream info]
    "tcp stream messages -> deserielise -> msg-handler"
    (s/consume
      (fn [data]
        (msg-handler
          stream
          (if (not (nil? data))
            (nippy/thaw data))))
      stream)))