(ns sharetribe.flex-cli.commands.notifications
  (:require [sharetribe.flex-cli.commands.notifications.preview :as notifications.preview]
            [sharetribe.flex-cli.commands.notifications.send :as notifications.send]))

(def cmd {:name "notifications"
          :sub-cmds [notifications.preview/cmd
                     notifications.send/cmd]})
