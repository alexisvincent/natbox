(ns dev
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require
    [clojure.java.javadoc :refer [javadoc]]
    [clojure.pprint :refer [pprint]]
    [clojure.reflect :refer [reflect]]
    [clojure.repl :refer [apropos dir doc find-doc pst source]]
    [clojure.tools.namespace.repl :refer [refresh refresh-all]]
    [natbox.core.kernel :as kernel]))


(def server-args ["server" "8000" "10.0.0.0/24"])
(def client-args ["client" "internal" "localhost" "8000"])

(def repl-instance nil)

(defn init
  "Creates and initializes the system under development in the Var
  #'system."
  []
  (alter-var-root #'repl-instance (constantly (apply kernel/init server-args))))

(defn start
  "Starts the system running, updates the Var #'system."
  []
  (kernel/start repl-instance))

(defn stop
  "Stops the system if it is currently running, updates the Var
  #'system."
  []
  (kernel/stop repl-instance)
  (alter-var-root #'repl-instance (constantly nil)))

(defn go
  "Initializes and starts the system running."
  []
  (init)
  (start))

(defn reset
  "Stops the system, reloads modified source files, and restarts it."
  []
  (if (not (nil? repl-instance)) (stop))
  (refresh :after `go))