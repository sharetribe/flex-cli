(ns sharetribe.flex-cli.commands.version
  (:require [sharetribe.flex-cli.io-util :as io-util]
            [sharetribe.flex-cli.cli-info :as cli-info]))

(defn version [opts _]
  (io-util/ppd
   [:span cli-info/version]))
