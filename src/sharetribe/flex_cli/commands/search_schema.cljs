(ns sharetribe.flex-cli.commands.search-schema
  (:require [sharetribe.flex-cli.commands.search-schema.list :as search-schema.list]
            [sharetribe.flex-cli.commands.search-schema.set :as search-schema.set]
            [sharetribe.flex-cli.commands.search-schema.unset :as search-schema.unset]))

(def cmd {:name "search"
          ;; Set to true, because handler is missing and we want Command not found error.
          :no-marketplace? true
          :sub-cmds [search-schema.list/cmd
                     search-schema.set/cmd
                     search-schema.unset/cmd]})
