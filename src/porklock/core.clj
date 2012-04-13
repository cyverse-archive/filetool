(ns porklock.core
  (:use [clojure.set]
        [porklock.fileops]
        [porklock.system]
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

(defn imkdir
  "Returns the path to the imkdir executable or an empty
   string if imkdir couldn't be found."
  []
  (find-file-in-path "imkdir"))

(defn iput
  "Returns the path to the iput executable or an empty
   string if iput couldn't be found."
  []
  (find-file-in-path "iput"))

(defn ils
  "Returns the path to the ils executable or an empty
   string if ils couldn't be found."
  []
  (find-file-in-path "ils"))

(defn validate
  [options]
  (let [paths-to-check (flatten [(files-to-transfer)
                                 (user-irods-dir)
                                 (irods-auth-filepath)
                                 (irods-env-filepath)
                                 (imkdir)
                                 (iput)
                                 (ils)])]
    (doseq [p paths-to-check]
      (if (not (ft/exists p))
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
    :default false]

   ["-g"
    "--get"
    "Retrieve files from iRODS. Use the --source as a path in iRODS and the --destination as a local directory."
    :default false]

   ["-m"
    "--mkdir"
    "Creates the directory in iRODS specified by --destination."
    :default false]))


