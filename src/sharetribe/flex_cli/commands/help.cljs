(ns sharetribe.flex-cli.commands.help
  (:require [sharetribe.flex-cli.io-util :as io-util]
            [clojure.string :as str]))

(defn list-commands
  "Recursively traverse through the list of commands and return a list
  of all commands and their sub-commands in a flat collection of [name
  desc] tuples."
  ([cmds] (list-commands cmds []))
  ([cmds parent-cmds]
   (mapcat
    (fn [{:keys [name desc sub-cmds]}]
      (concat
       (when desc
         [[(str/join " " (conj parent-cmds name)) desc]])
       (when sub-cmds
         (list-commands sub-cmds (conj parent-cmds name)))))
    cmds)))

(defn command-help
  [cmd]
  (->> cmd
       list-commands
       (sort-by first)
       io-util/align-cols
       (map (fn [[cmd desc]]
              [:span cmd "  " desc :line]))))

(defn help [opts ctx]
  (let [{:keys [commands]} ctx]
    (io-util/ppd
     [:span
      "CLI to interact with Sharetribe Flex" :line
      :line
      "VERSION" :line
      [:nest "0.0.1"] :line ;; Don't hardcode version
      :line
      "USAGE" :line
      [:nest "$ flex-cli [COMMAND]"] :line
      :line
      "COMMANDS" :line
      [:nest (command-help (:sub-cmds commands))]])))
