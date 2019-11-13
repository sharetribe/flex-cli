(ns sharetribe.flex-cli.commands.notifications
  (:require [sharetribe.flex-cli.commands.notifications.preview :as notifications.preview]))

(def cmd {:name "notifications"
          :sub-cmds [notifications.preview/cmd]})
