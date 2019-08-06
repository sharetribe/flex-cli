(ns sharetribe.flex-cli.command-exec
  "Namespace for executing a command after the CLI arguments are parsed"
  (:require [clojure.core.async :as async :refer [go <!]]
            [clojure.core.async.impl.protocols :as async.protocols]
            [sharetribe.flex-cli.credential-store :as credential-store]
            [sharetribe.flex-cli.api.client :as api-client]
            [sharetribe.flex-cli.exception :as exception]))

(defn- read-port?
  "Checks if x is read port. There's no predicate for `chan?`, thus this
  is the way to do it. See:
  https://clojure.atlassian.net/browse/ASYNC-74"
  [x]
  (satisfies? async.protocols/ReadPort x))

(defn- ensure-chan
  "Takes x and ensures it's a channel. If not, it will wrap the value in
  a go block (which returns values). Helps to normalize operations
  with may be async or sync."
  [x]
  (if (read-port? x)
    x
    (go x)))

;; Public

(defn execute
  "Execute a command after the CLI arguments have been parsed by
  dispatching to a corresponding command handler function."
  [parse-result commands]
  (go
    (let [{:keys [handler no-api-key? options arguments]} parse-result
          api-key (when-not no-api-key?
                    (<! (credential-store/get-api-key)))
          ctx (cond-> {:commands commands
                       :options options
                       :arguments arguments}

                (:marketplace options) (assoc :marketplace (:marketplace options))
                api-key (assoc :api-client (api-client/new-client api-key)))
          options (dissoc options :marketplace)]
      (<! (ensure-chan (handler options ctx))))))
