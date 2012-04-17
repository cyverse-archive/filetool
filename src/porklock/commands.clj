(ns porklock.commands
  (:use [porklock.pathing]
        [porklock.system]
        [porklock.shell-interop]
        [porklock.fileops :only [absify]]
        [clojure.pprint :only [pprint]])
  (:require [clojure.string :as string]
            [clojure-commons.file-utils :as ft]))

(defn icommands-env
  "Constructs an environment variable map for the icommands."
  []
  (let [default-map {"irodsAuthFileName" (irods-auth-filepath)
                     "irodsEnvFile"      (irods-env-filepath)}]
    (if (system-env "clientUserName")
      (merge default-map {"clientUserName" (system-env "clientUserName")})
      default-map)))

(defn imkdir
  [d env]
  (shell-out [(imkdir-path) "-p" d :env env]))

(defn ils
  [d env]
  (shell-out [(ils-path) :env env]))

(defn remote-create-dir
  [file-path env]
  (if (not (string/blank? (ft/dirname file-path)))
    (imkdir (ft/dirname file-path) env)))

(defn imkdir-command
  "Runs the imkdir command, creating a directory in iRODS."
  [options]
  (let [dest-dir (:destination options)
        ic-env   (icommands-env)]
    (imkdir dest-dir ic-env)))

(defn iput-command
  "Runs the iput icommand, tranferring files from the --source
   to the remote --destination."
  [options]
  (let [source-dir      (ft/abs-path (:source options))
        dest-dir        (:destination options)
        single-threaded (:single-threaded options)
        ic-env          (icommands-env)
        transfer-files  (files-to-transfer options)
        dest-files      (relative-dest-paths transfer-files source-dir dest-dir)]
    (pprint source-dir)
    (pprint transfer-files)
    (pprint dest-files)
    (doseq [[src dest]  (seq dest-files)]
      (remote-create-dir dest ic-env)
      (let [args-list (if single-threaded
                        [(iput-path) "-f" "-P" "-N 0" src dest :env ic-env]
                        [(iput-path) "-f" "-P" src dest :env ic-env])]
        (shell-out args-list)))))

(defn- iget-args
  [source destination single-threaded? env]
  (let [src-dir (ft/rm-last-slash source)
        dest    (ft/add-trailing-slash destination)]
    (cond
     (and (.endsWith source "/") single-threaded?)
     [(iget-path) "-f" "-P" "-r" "-N 0" src-dir dest :env env]
     
     (and (.endsWith source "/") (not single-threaded?))
     [(iget-path) "-f" "-P" "-r" src-dir dest :env env]
     
     (and (not (.endsWith source "/")) single-threaded?)
     [(iget-path) "-f" "-P" "-N 0" src-dir dest :env env]
     
     :else
     [(iget-path) "-f" "-P" src-dir dest :env env])))

(defn iget-command
  "Runs the iget icommand, retrieving files from --source
   to the local --destination."
  [options]
  (let [source (:source options)
        dest   (:destination options)
        ic-env (icommands-env)
        srcdir (ft/rm-last-slash source)
        args   (iget-args source dest (:single-threaded options) ic-env)]
    (shell-out args)))