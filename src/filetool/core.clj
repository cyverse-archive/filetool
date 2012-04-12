(ns filetool.core
  (:use [clojure.java.io :only (file)])
  (:require [clojure.tools.logging :as log]
            [clojure.tools.cli :as cli]
            [clojure.java.shell :as sh]
            [clojure.string :as string]
            [clojure-commons.file-utils :as ft])
  (:import [org.apache.commons.io FileUtils]
           [org.apache.commons.io.filefilter TrueFileFilter]))

(defn system-env
  "Returns values for the specified environment variable
   If no parameter is specified, then a map of all of
   environment variables is returned."
  ([]
     (System/getenv))
  ([var-name]
     (System/getenv var-name)))

(defn files-and-dirs
  "Returns a recursively listing of all files and subdirectories
   present under 'parent'."
  [parent]
  (map
   #(.getAbsolutePath %)
   (FileUtils/listFilesAndDirs (file parent) TrueFileFilter/INSTANCE TrueFileFilter/INSTANCE)))

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

(defn absify
  "Takes in a sequence of paths and turns them all into absolute paths."
  [paths]
  (map #(if (ft/abs-path? %1) %1 (ft/abs-path %1)) paths))

(defn exclude-files
  "Splits up the exclude option and turns them all into absolute paths."
  [{excludes :exclude delimiter :exclude-delimiter}]
  (map absify (string/split excludes (re-pattern delimiter))))


