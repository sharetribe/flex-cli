(ns sharetribe.flex-cli.commands.login
  (:require [clojure.core.async :as async :refer [go <!]]
            [sharetribe.flex-cli.credential-store :as credential-store]
            [sharetribe.flex-cli.io-util :as io-util]))

(defn login [opts _]
  (go
    (let [{:keys [api-key]} (<! (io-util/prompt [{:name :api-key
                                                  :type :password
                                                  :message "API key (copy-paste from Console)"}]))]

      ;; TODO Validate API key (by doing a request to API)?

      (credential-store/set-api-key api-key))))
