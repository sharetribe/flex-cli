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
       [[(str/join " " (conj parent-cmds name)) (or desc "")]]
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

(defn subcommand [sub-cmds name]
  (first (filter #(= name (:name %)) sub-cmds)))

(defn format-opt [opt-spec]
  (let [{:keys [short-opt long-opt desc required]} opt-spec
        opt (cond (and short-opt long-opt) (str short-opt ", " long-opt)
                  long-opt long-opt
                  short-opt short-opt)
        opt+req (if required
                  (str opt "=" required)
                  opt)]
    [opt+req desc]))

(defn opts-help [cmd]
  (->> (:opts cmd)
       (sort-by (juxt :short-opt :long-opt :desc))
       (map format-opt)
       (io-util/align-cols)
       (map (fn [[opt+req desc]]
              [:span opt+req "  " desc :line]))))

(defn subcommand-in [commands arguments]
  ;; Hmm... this functions knows too much about the "commands"
  ;; structure. Maybe it should be in the commands namespace instead.
  ;;
  ;; TODO Move functions that access the "commands" structure to
  ;; commands namespace. And then, solve the cyclic dependency that
  ;; will be caused by it.
  ;;
  (reduce
   (fn [cmd name]
     (subcommand (:sub-cmds cmd) name))
   commands
   arguments))

(defn subcommand-help [cmd args]
  (if-let [sub-cmd (subcommand-in cmd args)]
    [:span
     (:desc sub-cmd) :line
     :line
     "USAGE" :line
     [:nest "$ " (str/join " " (concat ["flex"] args))] :line
     (when (:opts sub-cmd)
       [:span
        :line
        "OPTIONS" :line
        [:nest (opts-help sub-cmd)]])]
    [:span "Command " (str/join " " args) " not found"]))

(defn main-help [cmd]
  [:span
   "CLI to interact with Sharetribe Flex" :line
   :line
   "VERSION" :line
   [:nest "0.0.1"] :line ;; Don't hardcode version
   :line
   "USAGE" :line
   [:nest "$ flex-cli [COMMAND]"] :line
   :line
   "COMMANDS" :line [:nest (command-help (:sub-cmds cmd))]]
  )

(defn help [opts ctx]
  (let [{:keys [commands arguments]} ctx]
    (io-util/ppd
     (if (seq arguments)
       (subcommand-help commands arguments)
       (main-help commands)))))

(comment
  (subcommand-in sharetribe.flex-cli.commands/command-definitions ["process" "list"])

  (sharetribe.flex-cli.core/main-dev-str "help process")
  )
