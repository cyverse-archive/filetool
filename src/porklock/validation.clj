(ns porklock.validation
  (:use [porklock.pathing]
        [slingshot.slingshot :only [try+ throw+]]
        [clojure-commons.error-codes])
  (:require [clojure-commons.file-utils :as ft]))

(defn validate-mkdir
  "Validates the info for a mkdir op.
   Throws an error in the input is invalid.

   For a mkdir op, all we need is the .irods
   files and the imkdir executable."
  [options]
  (let [paths-to-check (flatten [(user-irods-dir)
                                 (irods-auth-filepath)
                                 (irods-env-filepath)
                                 (imkdir-path)])]
    (doseq [p paths-to-check]
      (if (not (ft/exists? p))
        (throw+ {:error_code ERR_DOES_NOT_EXIST
                 :path p})))))

(defn validate-put
  "Validates information for a put operation.
   Throws an error if the input is invalid.
   
   For a put op, all of the local files must exist,
   all of the --include files must exist, all
   of the .irods/* files must exist, and the paths
   to the executable must exist."
  [options]
  (if-not (ft/dir? (:source options))
      (throw+ {:error_code ERR_NOT_A_FOLDER
               :path (:source options)}))
  
  (if-not (ft/abs-path? (:destination options))
    (throw+ {:error_code "ERR_PATH_NOT_ABSOLUTE"
             :path (:destination options)}))
  
  (let [paths-to-check (flatten [(files-to-transfer options)
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

(defn validate-get
  "Validates info for a get op. Throws an error
   on invalid input.

   For a get op, the following files must exist.
     * Path to 'iget'.
     * Destination directory.
     * .irods/.irodsA and .irods/.irodsEnv files.
   Additionally:
     * Destination must be a directory."
  [options]
  (let [paths-to-check (flatten [(user-irods-dir)
                                 (irods-auth-filepath)
                                 (irods-env-filepath)
                                 (iget-path)
                                 (:destination options)])]
    (doseq [p paths-to-check]
      (if (not (ft/exists? p))
        (throw+ {:error_code ERR_DOES_NOT_EXIST
                 :path p})))
    
    (if-not (ft/dir? (:destination options))
      (throw+ {:error_code ERR_NOT_A_FOLDER
               :path (:destination options)}))))