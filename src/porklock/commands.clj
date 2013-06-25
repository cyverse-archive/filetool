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

(def porkprint (partial println "[porklock] "))

(defn apply-metadata
  [cm destination meta]
  (let [tuples (map fix-meta meta)
        dest   (if (jg/is-dir? cm destination) (ft/rm-last-slash destination) destination)]
    (porkprint "Metadata tuples for " destination " are  " tuples)
    (when (pos? (count tuples))
      (doseq [tuple tuples]
        (porkprint "Size of tuple " tuple " is " (count tuple))
        (when (= (count tuple) 3)
          (porkprint "Might be adding metadata to " dest " " tuple)
          (porkprint "AVU? " dest (avu? cm dest (first tuple) (second tuple)))
          (when (empty? (avu? cm dest (first tuple) (second tuple)))
            (porkprint "Adding metadata " (first tuple) " " (second tuple) " " dest)
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
      (if-not (owns? cm username p )
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
          
          ;;; It's possible that the destination directory doesn't
          ;;; exist yet in iRODS, so create it if it's not there.
          (porkprint "Creating all directories in iRODS down to " dir-dest)
          (when-not (exists? cm dir-dest)
            (mkdirs cm dir-dest))
          
          ;;; The destination directory needs to be tagged with AVUs
          ;;; for the App and Execution.
          (porkprint "Applying metadata to" dir-dest)
          (apply-metadata cm dir-dest metadata)
          
          ;;; If the destination directory is the user's home directory,
          ;;; then the set-parent-owner function may recurse up to root
          ;;; in iRODS. The user needs to own the directories down to
          ;;; this destination directory. They shouldn't be able to select
          ;;; a directory inside a directory that they don't own, so this
          ;;; should be safe.
          (when-not (= (user-home-dir cm (:user options)) dir-dest)
            (porkprint "Setting the owner for parent directories of " dir-dest " to " (:user options)) 
            (set-parent-owner cm (:user options) dir-dest))
          
          ;;; Since we run as a proxy account, the destination directory
          ;;; needs to have the owner set to the user that ran the app.
          (when-not (owns? cm (:user options) dir-dest)
            (porkprint "Setting owner of " dir-dest " to " (:user options))
            (set-owner cm dir-dest (:user options)))
          
          (shell-out [(iput-path) "-f" "-P" src dest :env ic-env])
          
          ;;; After the file has been uploaded, the user needs to be
          ;;; made the owner of it.
          (when-not (owns? cm (:user options) dest)
            (porkprint "Setting owner of " dest " to " (:user options))
            (set-owner cm dest (:user options)))
          
          ;;; Apply the App and Execution metadata to the newly uploaded
          ;;; file/directory.
          (porkprint "Applying metadata to " dest)
          (apply-metadata cm dest metadata)))
       
      (when (and (exists? cm dest-dir) (not skip-parent?))
        (porkprint "Applying metadata to " dest-dir)
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

