(ns sharetribe.flex-cli.commands.logout
  (:require [clojure.core.async :as async :refer [go <!]]
            [sharetribe.flex-cli.credential-store :as credential-store]
            [sharetribe.flex-cli.io-util :as io-util]))

(defn logout [opts _]
  (go
    (<! (credential-store/delete-api-key))

    (io-util/ppd
     [:span "Successfully logged out."])))
