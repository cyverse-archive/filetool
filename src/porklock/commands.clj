(ns porklock.commands
  (:use [porklock.pathing]
        [porklock.system]
        [porklock.config]
        [porklock.shell-interop]
        [clj-jargon.jargon :as jg]
        [porklock.fileops :only [absify]]
        [clojure.pprint :only [pprint]])
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure-commons.file-utils :as ft]))

(defn init-jargon
  [cfg-path]
  (load-config-from-file cfg-path)
  (jg/init (irods-host)
           (irods-port)
           (irods-user)
           (irods-pass)
           (irods-home)
           (irods-zone)
           (irods-resc)))

(defn fix-meta
  [m]
  (cond 
    (= (count m) 3) m
    (= (count m) 2) (conj m "default-unit")
    (= (count m) 1) (concat m ["default-value" "default-unit"])
    :else           []))

(defn apply-metadata
  [cm dest meta]
  (let [tuples (map fix-meta meta)]
    (when (pos? (count tuples))
      (doseq [tuple tuples]
        (if (= (count tuple) 3)
          (apply (partial set-metadata cm dest) tuple))))))

(defn irods-env-contents
  [options]
  (str
    "irodsHost "     (irods-host) "\n"
    "irodsPort "     (irods-port) "\n"
    "irodsUserName " (irods-user) "\n"
    "irodsZone "     (irods-zone) "\n"
    "irodsHome "     (irods-home) "\n"))

(defn make-irods-env
  [env]
  (shell-out [(iinit-path) :in (irods-pass) :env env]))

(defn icommands-env
  "Constructs an environment variable map for the icommands."
  [options]
  (let [env {"irodsAuthFileName" (irods-auth-filepath)
             "irodsEnvFile"      (irods-env-filepath)}]
    (spit (irods-env-filepath) (irods-env-contents options))
    (make-irods-env env)
    (merge env {"clientUserName" (:user options)})))

(defn iput-command
  "Runs the iput icommand, tranferring files from the --source
   to the remote --destination."
  [options]
  (let [source-dir      (ft/abs-path (:source options))
        dest-dir        (:destination options)
        irods-cfg       (init-jargon (:config options))
        ic-env          (icommands-env options)
        transfer-files  (files-to-transfer options)
        metadata        (:meta options)
        dest-files      (relative-dest-paths transfer-files source-dir dest-dir)]
    (jg/with-jargon irods-cfg [cm]
      (doseq [[src dest]  (seq dest-files)]
        (let [dir-dest (ft/dirname dest)]
          (when-not (exists? cm dir-dest)
            (mkdirs cm dir-dest)
            (apply-metadata cm dir-dest metadata))
          (when-not (owns? cm (:user options) dir-dest)
            (set-owner cm dir-dest (:user options)))
          (shell-out [(iput-path) "-f" "-P" src dest :env ic-env])
          (when-not (owns? cm (:user options) dest)
            (set-owner cm dest (:user options)))
          (apply-metadata cm dest metadata))))))

(defn- iget-args
  [source destination env]
  (filter #(not (nil? %)) 
          [(iget-path) 
           "-f" 
           "-P" 
           (if (.endsWith source "/") 
             "-r")
           (ft/rm-last-slash source)
           (ft/add-trailing-slash destination)
           :env env]))

(defn iget-command
  "Runs the iget icommand, retrieving files from --source
   to the local --destination."
  [options]
  (let [source    (:source options)
        dest      (:destination options)
        irods-cfg (init-jargon (:config options))
        ic-env    (icommands-env options)
        srcdir    (ft/rm-last-slash source)
        args      (iget-args source dest ic-env)]
    (shell-out args)))
