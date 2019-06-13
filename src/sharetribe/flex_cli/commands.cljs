(ns sharetribe.flex-cli.commands
  (:require [clojure.spec.alpha :as s]
            [sharetribe.flex-cli.commands.auth :as auth]
            [sharetribe.flex-cli.commands.help :as help]
            [sharetribe.flex-cli.commands.marketplace :as marketplace]
            [sharetribe.flex-cli.commands.process :as process]
            [sharetribe.flex-cli.commands.version :as version]))

(def marketplace-opt
  {:id :marketplace
   :long-opt "--marketplace"
   :short-opt "-m"
   :required "MARKETPLACE IDENT"})

(def command-definitions
  {:handler ::main
   :opts [{:id :help
           :long-opt "--help"
           :short-opt "-h"}
          {:id :version
           :long-opt "--version"
           :short-opt "-V"}]
   :sub-cmds
   [{:name "help"
     :handler help/help}
    {:name "version"
     :handler version/version}
    {:name "login"
     :handler auth/login
     :opts [
            ;; TODO Remove this! This is temporary, just for
            ;; testing. Prompt the API key instead.
            {:id :api-key
             :long-opt "--api-key"
             :required "API KEY"
             :missing "--api-key is required"}]}
    {:name "logout"
     :handler auth/logout}
    {:name "marketplace"
     :handler marketplace/marketplace
     :sub-cmds
     [{:name "list"
       :handler marketplace/list}]}
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

(s/def ::id keyword?)
(s/def ::desc string?)
(s/def ::long-opt string?)
(s/def ::short-opt string?)
(s/def ::required string?)
(s/def ::opt (s/keys :req-un [::id
                              ::long-opt]
                     :opt-un [::short-opt
                              ::desc
                              ::required]))
(s/def ::opts (s/coll-of ::opt))
(s/def ::sub-cmds (s/coll-of ::sub-cmd))
(s/def ::handler any?)
(s/def ::no-marketplace? boolean?)
(s/def ::name string?)
(s/def ::sub-cmd (s/keys :req-un [::name]
                         :opt-un [::desc
                                  ::handler
                                  ::opts
                                  ::no-marketplace?
                                  ::sub-cmds]))
(s/def ::root-cmd (s/keys :opt-un [::desc
                                   ::handler
                                   ::opts
                                   ::no-marketplace?
                                   ::sub-cmds]))

(defn main [parse-result]
  (let [{:keys [options]} parse-result]
    (cond
      ;; dissoc :help option because this is how the "help" command is
      ;; invoked
      (:help options) (help/help (dissoc options :help))
      (:version options) (version/version (dissoc options :version)) ;; Same

      :else (help/help {}) ;; show help as a default

      )))

(defn handle [parse-result]
  (let [{:keys [handler options]} parse-result]
    (if (= ::main handler)
      (main parse-result)
      (handler options))))
