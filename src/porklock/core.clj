(ns porklock.core
  (:gen-class)
  (:use [clojure.set]
        [porklock.fileops]
        [porklock.system]
        [porklock.shell-interop]
        [slingshot.slingshot :only [try+ throw+]]
        [clojure-commons.error-codes])
  (:require [clojure.tools.logging :as log]
            [clojure.tools.cli :as cli]
            [clojure.java.shell :as sh]
            [clojure.string :as string]
            [clojure-commons.file-utils :as ft]))

(defn exclude-files
  "Splits up the exclude option and turns them all into absolute paths."
  [{excludes :exclude delimiter :exclude-delimiter}]
  (absify (string/split excludes (re-pattern delimiter))))

(defn include-files
  "Splits up the include option and turns them all into absolute paths."
  [{includes :include delimiter :include-delimiter}]
  (absify (string/split includes (re-pattern delimiter))))

(defn files-to-transfer
  "Constructs a list of the files that need to be transferred."
  [options]
  (let [includes (set (include-files options))
        excludes (set (exclude-files options))
        allfiles (set (files-and-dirs (:source options)))]
    (seq (union (difference allfiles excludes) includes))))

(defn- str-contains?
  [s match]
  (if (not= (.indexOf s match) -1)
    true
    false))

(defn relative-dest-paths
  "Constructs a list of relative destination paths based on the
   input and the given source directory."
  [transfer-files source-dir]
  
  (let [sdir (ft/add-trailing-slash source-dir)]
    (apply merge (map
                  #(if (and (str-contains? %1 sdir))
                     {%1 (string/replace %1 (re-pattern sdir) "")} 
                     {%1 %1})
                  transfer-files))))

(defn user-irods-dir
  "Returns the full path to the user's .irods directory."
  []
  (ft/path-join (user-home) ".irods"))

(defn irods-auth-filepath
  "Returns the path where the .irodsA should be."
  []
  (ft/path-join (user-irods-dir) ".irodsA"))

(defn irods-env-filepath
  "Returns the path to where the .irodsEnv file should be."
  []
  (ft/path-join (user-irods-dir) ".irodsEnv"))

(defn icommands-env
  "Constructs an environment variable map for the icommands."
  []
  (let [default-map {"irodsAuthFileName" (irods-auth-filepath)
                     "irodsEnvFile"      (irods-env-filepath)}]
    (if (system-env "clientUserName")
      (merge default-map {"clientUserName" (system-env "clientUserName")})
      default-map)))

(defn imkdir-path
  "Returns the path to the imkdir executable or an empty
   string if imkdir couldn't be found."
  []
  (find-file-in-path "imkdir"))

(defn iput-path
  "Returns the path to the iput executable or an empty
   string if iput couldn't be found."
  []
  (find-file-in-path "iput"))

(defn iget-path
  "Returns the path to the iget executable, or an empty
   string if iget cannot be found."
  []
  (find-file-in-path "iget"))

(defn ils-path
  "Returns the path to the ils executable or an empty
   string if ils couldn't be found."
  []
  (find-file-in-path "ils"))

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
  (let [source-dir      (:source options)
        dest-dir        (:destination options)
        single-threaded (:single-threaded options)
        ic-env          (icommands-env)
        transfer-files  (files-to-transfer options)
        dest-files      (relative-dest-paths transfer-files source-dir)]
    (doseq [[src dest]  (seq dest-files)]
      (remote-create-dir dest ic-env)
      (let [full-dest (ft/path-join dest-dir dest)
            args-list (if single-threaded
                        [(iput-path) "-f" "-P" "-N 0" src full-dest :env ic-env]
                        [(iput-path) "-f" "-P" src full-dest :env ic-env])]
        (shell-out args-list)))))

(defn- iget-args
  [source dest single-threaded? env]
  (let [src-dir (ft/rm-last-slash source)]
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

(defn validate
  [options]
  (let [paths-to-check (flatten [(files-to-transfer)
                                 (user-irods-dir)
                                 (irods-auth-filepath)
                                 (irods-env-filepath)
                                 (imkdir-path)
                                 (iput-path)
                                 (ils-path)])]
    (doseq [p paths-to-check]
      (if (not (ft/exists? p))
        (throw+ {:error_code ERR_DOES_NOT_EXIST
                 :path p})))))

(defn settings
  [args]
  (cli/cli
   args
   ["-e"
    "--exclude"
    "List of files to be excluded from uploads."
    :default ""]
   
   ["-x"
    "--exclude-delimiter"
    "Delimiter for the list of files to be excluded from uploads"
    :default ","]

   ["-i"
    "--include"
    "List of files to make sure are uploaded"
    :default ""]

   ["-n"
    "--include-delimiter"
    "Delimiter for the list of files that should be included in uploads."
    :default ","]

   ["-s"
    "--source"
    "The directory containing files to be transferred."
    :default "."]

   ["-d"
    "--destination"
    "The destination directory in iRODS."
    :default ""]

   ["-t"
    "--single-threaded"
    "Tells the i-commands to only use a single thread."
    :flag true]

   ["-g"
    "--get"
    "Retrieve files from iRODS. Use the --source as a path in iRODS and the --destination as a local directory."
    :flag true]

   ["-m"
    "--mkdir"
    "Creates the directory in iRODS specified by --destination."
    :flag true]

   ["-h"
    "--help"
    "Prints this help."
    :flag true]))

(defn -main
  [& args]
  (try+
   (let [[options remnants banner] (settings args)]
     (when (:help options)
       (println banner)
       (System/exit 0))

     (when (:mkdir options)
       (imkdir-command options)
       (System/exit 0))

     (when (:get options)
       (iget-command options)
       (System/exit 0))

     (when (:put options)
       (iput-command options)
       (System/exit 0))

     (println banner)
     (System/exit 1))
   (catch java.lang.Exception e
     (println e)
     (System/exit 2))))


