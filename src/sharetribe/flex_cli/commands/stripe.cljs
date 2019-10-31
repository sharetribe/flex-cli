(ns sharetribe.flex-cli.commands.stripe
  (:require [sharetribe.flex-cli.commands.stripe.update-version :as stripe.update-version]))

(def cmd {:name "stripe"
          :sub-cmds [stripe.update-version/cmd]})

