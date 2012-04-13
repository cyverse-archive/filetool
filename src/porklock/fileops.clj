(ns porklock.fileops
  (:use [clojure.java.io :only (file)])
  (:require [clojure-commons.file-utils :as ft])
  (:import [org.apache.commons.io FileUtils]
           [org.apache.commons.io.filefilter TrueFileFilter]))

(defn files-and-dirs
  "Returns a recursively listing of all files and subdirectories
   present under 'parent'."
  [parent]
  (map
    #(.getAbsolutePath %)
    (FileUtils/listFiles 
      (file parent) 
      TrueFileFilter/INSTANCE 
      TrueFileFilter/INSTANCE)))

(defn absify
  "Takes in a sequence of paths and turns them all into absolute paths."
  [paths]
  (map #(if (ft/abs-path? %1) %1 (ft/abs-path %1)) paths))

(defn user-home
  "Returns the path to the user's home directory."
  []
  (FileUtils/getUserDirectoryPath))

(defn filenames-in-dir
  "Grabs all of the filenames in a directory."
  [dirpath]
  (map 
    #(.getName %)  
    (FileUtils/listFiles (file dirpath) TrueFileFilter/INSTANCE nil)))