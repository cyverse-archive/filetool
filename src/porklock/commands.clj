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

(defn avu?
  [cm path attr value]
  (filter #(= value (:value %)) (get-attribute cm path attr)))

(defn apply-metadata
  [cm dest meta]
  (let [tuples (map fix-meta meta)]
    (when (pos? (count tuples))
      (doseq [tuple tuples]
        (when (= (count tuple) 3)
          (println "Might be adding metadata to dest " tuple)
          (println "AVU? " dest (avu? cm dest (first tuple) (second tuple)))
          (when (empty? (avu? cm dest (first tuple) (second tuple)))
            (println "Adding metadata " (first tuple) " " (second tuple) " " dest)
            (apply (partial add-metadata cm dest) tuple)))))))

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

(defn user-home-dir
  [cm username]
  (ft/path-join "/" (:zone cm) "home" username))

(defn set-parent-owner
  [cm username dir-dest]
  (loop [p (ft/dirname dir-dest)]
    (when-not (= (ft/rm-last-slash p) (user-home-dir cm username))
      (if-not (owns? cm p username)
        (set-owner cm p username))
      (recur (ft/dirname p)))))

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
        skip-parent?    (:skip-parent-meta options)
        dest-files      (relative-dest-paths transfer-files source-dir dest-dir)]
    (jg/with-jargon irods-cfg [cm]
      (doseq [[src dest]  (seq dest-files)]
        (let [dir-dest (ft/dirname dest)]
          (println "Destination: " dir-dest)
          (when-not (exists? cm dir-dest)
            (mkdirs cm dir-dest))
          (apply-metadata cm dir-dest metadata)
          (when-not (= (user-home-dir cm (:user options)) dir-dest)
            (set-parent-owner cm (:user options) dir-dest))
          (when-not (owns? cm (:user options) dir-dest)
            (set-owner cm dir-dest (:user options)))
          (shell-out [(iput-path) "-f" "-P" src dest :env ic-env])
          (when-not (owns? cm (:user options) dest)
            (set-owner cm dest (:user options)))
          (apply-metadata cm dest metadata)))
      (when (and (exists? cm dest-dir) (not skip-parent?))
        (println "Applying metadata to " dest-dir)
        (apply-metadata cm dest-dir metadata)))))

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

(defn apply-input-metadata
  [cm user fpath meta]
  (if-not (jg/is-dir? cm fpath)
    (if (jg/owns? cm user fpath)
      (apply-metadata cm fpath meta))
    (doseq [f (file-seq (jg/file fpath))]
      (let [abs-path (.getAbsolutePath f)]
        (if (jg/owns? cm user abs-path)
          (apply-metadata cm abs-path meta))))))

(defn iget-command
  "Runs the iget icommand, retrieving files from --source
   to the local --destination."
  [options]
  (let [source    (:source options)
        dest      (:destination options)
        irods-cfg (init-jargon (:config options))
        ic-env    (icommands-env options)
        srcdir    (ft/rm-last-slash source)
        args      (iget-args source dest ic-env)
        metadata  (:meta options)]
    (jg/with-jargon irods-cfg [cm]
      (apply-input-metadata cm (:user options) source metadata)
      (shell-out args))))

