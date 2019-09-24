(ns sharetribe.flex-cli.command-exec
  "Namespace for executing a command after the CLI arguments are parsed"
  (:require [clojure.core.async :as async :refer [go <!]]
            [clojure.core.async.impl.protocols :as async.protocols]
            [sharetribe.flex-cli.credential-store :as credential-store]
            [sharetribe.flex-cli.async-util :refer [ensure-chan]]
            [sharetribe.flex-cli.api.client :as api-client]
            [sharetribe.flex-cli.exception :as exception]))

;; Public

(defn execute
  "Execute a command after the CLI arguments have been parsed by
  dispatching to a corresponding command handler function."
  [parse-result commands]
  (go
    (let [{:keys [handler no-api-key? options arguments]} parse-result
          api-key (when-not no-api-key? (credential-store/get-api-key))
          ctx (cond-> {:commands commands
                       :options options
                       :arguments arguments}

                (:marketplace options) (assoc :marketplace (:marketplace options))
                api-key (assoc :api-client (api-client/new-client api-key)))
          options (dissoc options :marketplace)]
      (<! (ensure-chan (handler options ctx))))))
