(ns filetool.core
  (:require [clojure.tools.logging :as log]
            [clojure.tools.cli :as cli]
            [clojure.java.shell :as sh]
            [clojure.string :as string]
            [clojure-commons.file-utils :as ft]))

(defn system-env
  "Returns values for the specified environment variable
   If no parameter is specified, then a map of all of
   environment variables is returned."
  ([]
     (System/getenv))
  ([var-name]
     (System/getenv var-name)))

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
