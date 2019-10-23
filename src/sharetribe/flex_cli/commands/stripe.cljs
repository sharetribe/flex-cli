(ns sharetribe.flex-cli.commands.stripe
  (:require [sharetribe.flex-cli.commands.stripe.update-version :as stripe.update-version]))

(def cmd {:name "stripe"
          ;; Set to true, because handler is missing and we want Command not found error.
          :no-marketplace? true
          :sub-cmds [stripe.update-version/cmd]})

