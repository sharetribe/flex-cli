(ns sharetribe.flex-cli.commands.process.pull
  (:require [clojure.core.async :as async :refer [go <!]]
            [sharetribe.flex-cli.api.client :refer [do-get <?]]
            [sharetribe.flex-cli.io-util :as io-util]
            [sharetribe.flex-cli.exception :as exception]))

(declare pull-process)

(def cmd {:name "pull"
          :handler #'pull-process
          :desc "fetch a process file"
          :opts [{:id :process-name
                  :long-opt "--process"
                  :required "[WIP] PROCESS_NAME"
                  :missing "--process is required"}
                 {:id :version
                  :long-opt "--version"
                  :required "[WIP] VERSION"}
                 {:id :alias
                  :long-opt "--alias"
                  :required "[WIP] ALIAS"}
                 {:id :path
                  :long-opt "--path"
                  :required "[WIP] LOCAL_PROCESS_DIR"
                  :missing "--path is required"}
                 {:id :force
                  :long-opt "--force"}]})

(defn- process-dir? [path]
  (io-util/file? (io-util/join path "process.edn")))

(defn- ensure-process-dir! [path force]
  (cond
    (io-util/file? path) (exception/throw! :command/invalid-args
                                           {:command :pull
                                            :errors ["--path should be a directory"]})

    (not (io-util/dir? path)) (do (io-util/log "Creating a new directory:" path)
                                  (io-util/mkdirp path))

    (and (process-dir? path) (not force)) (exception/throw! :command/invalid-args
                                                            {:command :pull
                                                             :errors ["--path is already a process directory, use --force to overwrite"]})))

(defn pull-process [params ctx]
  (go
    (let [{:keys [api-client marketplace]} ctx
          {:keys [process-name version alias path force]} params
          _ (when-not (or version alias)
              (exception/throw! :command/invalid-args {:command :pull
                                                       :errors ["--version or --alias is required"]}))
          _ (ensure-process-dir! path force)

          query-params (cond-> {:marketplace marketplace
                                :process process-name}
                         alias (assoc :alias alias)
                         version (assoc :version version))

          res (<? (do-get api-client "/processes/show" query-params))

          process-data (-> res :data :process/process)
          process-file-path (io-util/join path "process.edn")]

      (io-util/save-file process-file-path (with-out-str (cljs.pprint/pprint process-data)))
      (io-util/log "Saved process to" process-file-path))))
