(ns sharetribe.flex-cli.commands.logout
  (:require [sharetribe.flex-cli.credential-store :as credential-store]
            [sharetribe.flex-cli.io-util :as io-util]))

(defn logout [opts _]
  (credential-store/delete-api-key)

  (io-util/ppd
   [:span "Successfully logged out."]))
