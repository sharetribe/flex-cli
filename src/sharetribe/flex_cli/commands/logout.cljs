(ns sharetribe.flex-cli.commands.logout
  (:require [sharetribe.flex-cli.credential-store :as credential-store]))

(defn logout [opts _]
  (credential-store/delete-api-key))
