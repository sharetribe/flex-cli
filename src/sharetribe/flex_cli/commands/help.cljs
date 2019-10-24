(ns sharetribe.flex-cli.commands.help
  (:require [sharetribe.flex-cli.io-util :as io-util]
            [sharetribe.flex-cli.cli-info :as cli-info]
            [sharetribe.flex-cli.command-util :as command-util]
            [sharetribe.flex-cli.view :as view]
            [chalk]
            [clojure.string :as str]))

(defn list-commands
  "Recursively traverse through the list of commands and return a list
  of all commands and their sub-commands in a flat collection of [name
  desc] tuples."
  ([cmds] (list-commands cmds []))
  ([cmds parent-cmds]
   (->> cmds
        (remove :hidden?)
        (mapcat
         (fn [{:keys [name desc sub-cmds handler]}]
           (concat
            (when handler [[(str/join " " (conj parent-cmds name)) (or desc "")]])
            (when sub-cmds
              (list-commands sub-cmds (conj parent-cmds name)))))))))

(defn command-help
  [cmd]
  (->> cmd
       list-commands
       (sort-by first)
       view/align-cols
       (map (fn [[cmd desc]]
              [:span cmd "  " desc]))
       (view/interpose-some :line)))

(defn format-opt [opt-spec]
  (let [{:keys [short-opt long-opt desc required]} opt-spec
        opt (view/join-some ", " [short-opt long-opt])
        opt+req (view/join-some "=" [opt required])]
    [opt+req desc]))

(defn opts-help [opts]
  (->> opts
       (sort-by (juxt :short-opt :long-opt :desc))
       (map format-opt)
       view/align-cols
       (map (fn [[opt+req desc]]
              [:span opt+req "  " desc]))
       (view/interpose-some :line)))

;; View components

(defn page
  "Component for a 'page'. Page consists of sections that are separated
  with line breaks."
  [& sections]
  [:span
   (view/interpose-some :line sections)
   :line ;; add one line break at the end to get some extra space
   ])

(defn title
  "White bold title."
  [s]
  [:span (.bold.white chalk s) :line])

(defn section
  "Section with title and indented content."
  [title-str content]
  [:span
   (title title-str)
   [:nest content] :line])

(defn usage
  "Usage section"
  ([] (usage ["[COMMAND]"]))
  ([args]
   [:span "$ " (str/join " " (concat [cli-info/bin] args))]))

;; Command handlers

(defn subcommand-help [cmd args]
  (if-let [sub-cmd (command-util/subcommand-in cmd args)]
    (page

     (when-let [desc (:desc sub-cmd)]
       [:span desc :line])

     (section
      "USAGE"
      (usage args))

     (when-let [opts (:opts sub-cmd)]
       (section
        "OPTIONS"
        (opts-help opts))))

    [:span "Command " (str/join " " args) " not found"]))

(defn main-help [cmd]
  (page

   [:span "CLI to interact with Sharetribe Flex" :line]

   (section
    "VERSION"
    cli-info/version)

   (section
    "USAGE"
    (usage))

   (section
    "COMMANDS"
    (command-help (:sub-cmds cmd)))

   [:span "Subcommand help:"]
   [:nest "$ " cli-info/bin " help [COMMAND]"]))

(defn help [opts ctx]
  (let [{:keys [commands arguments]} ctx]
    (io-util/ppd
     (if (seq arguments)
       (subcommand-help commands arguments)
       (main-help commands)))))

(comment
  (command-util/subcommand-in sharetribe.flex-cli.commands/command-definitions ["process" "list"])

  (sharetribe.flex-cli.core/main-dev-str "help process")
  )
