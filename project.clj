(defproject porklock "1.2.1-SNAPSHOT"
  :description "A command-line tool for interacting with iRODS."
  :main porklock.core
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.logging "0.2.3"]
                 [org.iplantc/clojure-commons "1.4.1-SNAPSHOT"]
                 [org.clojure/tools.cli "0.2.1"]
                 [commons-io/commons-io "2.2"]
                 [slingshot "0.10.3"]
                 [org.iplantc/clj-jargon "0.2.4-SNAPSHOT"]]
  :plugins [[org.iplantc/lein-iplant-rpm "1.4.1-SNAPSHOT"]]
  :iplant-rpm {:summary "Porklock"
               :type :command
               :exe-files ["curl_wrapper.pl"]}
  :repositories {"iplantCollaborative"
                 "http://projects.iplantcollaborative.org/archiva/repository/internal/"})
