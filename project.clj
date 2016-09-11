(defproject natbox "0.1.0-SNAPSHOT"

  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [manifold "0.1.5"]
                 [aleph "0.4.2-alpha8"]
                 [com.taoensso/nippy "2.12.1"]
                 [pandect "0.6.0"]
                 [kovacnica/clojure.network.ip "0.1.1"]
                 [org.clojure/core.match "0.3.0-alpha4"]]

  :main natbox.core.cli

  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]]
                   :source-paths ["dev"]}})
