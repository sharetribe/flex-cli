(ns sharetribe.flex-cli.commands.auth
  (:require [clojure.core.async :as async :refer [go <!]]
            [sharetribe.flex-cli.credential-store :as credential-store]))

(defn login [opts _]
  ;; TODO Validate API key (by doing a request to API)?

  (credential-store/set-api-key (:api-key opts)))

(defn logout [opts _]
  (credential-store/delete-api-key))

(comment
  (with-out-str
    (sharetribe.flex-cli.core/main-dev-str "login --api-key=082ef1755d9979b2367f8e1bf1115677b5a064dd"))
  (sharetribe.flex-cli.core/main-dev-str "logout"))
