(defproject porklock "1.0.0-SNAPSHOT"
  :description "A command-line tool for interacting with iRODS."
  :main porklock.core
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/tools.logging "0.2.3"]
                 [org.iplantc/clojure-commons "1.1.0-SNAPSHOT"]
                 [org.clojure/tools.cli "0.2.1"]
                 [commons-io/commons-io "2.2"]
                 [slingshot "0.10.2"]]
  :dev-dependencies [[org.iplantc/lein-iplant-rpm "1.1.0-SNAPSHOT"]]
  :iplant-rpm {:summary "Porklock"
               :type :command
               :release 1
               :provides "iplant-porklock"
               :exe-files ["curl_wrapper.pl"]}
  :repositories {"iplantCollaborative"
                 "http://projects.iplantcollaborative.org/archiva/repository/internal/"})
