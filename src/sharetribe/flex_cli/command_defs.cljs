(ns sharetribe.flex-cli.command-defs
  (:require [sharetribe.flex-cli.commands.login :as login]
            [sharetribe.flex-cli.commands.logout :as logout]
            [sharetribe.flex-cli.commands.help :as help]
            [sharetribe.flex-cli.commands.main :as main]
            [sharetribe.flex-cli.commands.process :as process]
            [sharetribe.flex-cli.commands.stripe :as stripe]
            [sharetribe.flex-cli.commands.version :as version]))

(def marketplace-opt
  {:id :marketplace
   :long-opt "--marketplace"
   :short-opt "-m"
   :desc "marketplace identifier"
   :required "MARKETPLACE_ID"
   :missing "--marketplace is required"})

(def command-definitions
  {:handler main/main
   :no-api-key? true
   :no-marketplace? true
   :opts [{:id :help
           :long-opt "--help"
           :short-opt "-h"}
          {:id :version
           :long-opt "--version"
           :short-opt "-V"}]
   :sub-cmds
   [{:name "help"
     :desc "display help for Flex CLI"
     :no-api-key? true
     :no-marketplace? true
     :catch-all? true
     :handler help/help}
    {:name "version"
     :no-api-key? true
     :no-marketplace? true
     :hidden? true
     :handler version/version}
    {:name "login"
     :desc "log in with API key"
     :no-api-key? true
     :no-marketplace? true
     :handler login/login}
    {:name "logout"
     :desc "logout"
     :no-api-key? true
     :no-marketplace? true
     :handler logout/logout}
    stripe/cmd
    process/cmd]})

(defn- with-marketplace-opt [cmd]
  (if (:no-marketplace? cmd)
    cmd
    (update cmd :opts conj marketplace-opt)))

(defn format-command-def [command-def]
  (cond-> command-def
    true with-marketplace-opt
    (seq (:sub-cmds command-def)) (update :sub-cmds #(map format-command-def %))))

(def commands (format-command-def command-definitions))
