(ns natbox.core.cli
  (:require [natbox.core.kernel :as kernel]))

(defn -main [& args]
  (let [instance (apply kernel/init args)]
    (kernel/start instance)
    (kernel/stop instance)))
