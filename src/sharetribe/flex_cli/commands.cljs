(ns sharetribe.flex-cli.commands
  (:require [clojure.core.async :as async :refer [go <!]]
            [clojure.core.async.impl.protocols :as async.protocols]
            [clojure.spec.alpha :as s]
            [sharetribe.flex-cli.credential-store :as credential-store]
            [sharetribe.flex-cli.api.client :as api-client]
            [sharetribe.flex-cli.commands.auth :as auth]
            [sharetribe.flex-cli.commands.help :as help]
            [sharetribe.flex-cli.commands.marketplace :as marketplace]
            [sharetribe.flex-cli.commands.process :as process]
            [sharetribe.flex-cli.commands.version :as version]
            [sharetribe.flex-cli.exception :as exception]))

(def marketplace-opt
  {:id :marketplace
   :long-opt "--marketplace"
   :short-opt "-m"
   :required "MARKETPLACE IDENT"
   :missing "--marketplace is required"})

(declare main)

(def command-definitions
  {:handler #'main
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
     :handler version/version}
    {:name "login"
     :desc "log in with API key"
     :no-api-key? true
     :no-marketplace? true
     :handler auth/login
     :opts [
            ;; TODO Remove this! This is temporary, just for
            ;; testing. Prompt the API key instead.
            {:id :api-key
             :long-opt "--api-key"
             :required "API KEY"
             :missing "--api-key is required"}]}
    {:name "logout"
     :desc "logout"
     :no-api-key? true
     :no-marketplace? true
     :handler auth/logout}
    {:name "marketplace"
     :no-marketplace? true
     :handler marketplace/marketplace
     :sub-cmds
     [{:name "list"
       :no-marketplace? true
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
(s/def ::no-api-key? boolean?)
(s/def ::name string?)
(s/def ::sub-cmd (s/keys :req-un [::name]
                         :opt-un [::desc
                                  ::handler
                                  ::opts
                                  ::no-marketplace?
                                  ::no-api-key?
                                  ::sub-cmds]))
(s/def ::root-cmd (s/keys :opt-un [::desc
                                   ::handler
                                   ::opts
                                   ::no-marketplace?
                                   ::no-api-key?
                                   ::sub-cmds]))

(defn main [options ctx]
  (cond
    ;; dissoc :help option because this is how the "help" command is
    ;; invoked
    (:help options) (help/help (dissoc options :help) ctx)
    (:version options) (version/version (dissoc options :version) ctx) ;; Same

    :else (help/help options ctx) ;; show help as a default
    ))

(defn- read-port?
  "Checks if x is read port. There's no predicate for `chan?`, thus this
  is the way to do it. See:
  https://clojure.atlassian.net/browse/ASYNC-74"
  [x]
  (satisfies? async.protocols/ReadPort x))

(defn- ensure-chan
  "Takes x and ensures it's a channel. If not, it will wrap the value in
  a go block (which returns values). Helps to normalize operations
  with may be async or sync."
  [x]
  (if (read-port? x)
    x
    (go x)))

(defn handle [parse-result]
  (go
    (let [{:keys [handler no-api-key? options arguments]} parse-result
          api-key (when-not no-api-key?
                    (<! (credential-store/get-api-key)))
          ctx (cond->
                  ;; The help command needs to know about all the
                  ;; commands, but it can't refer to commands
                  ;; namespaces because that would create cyclic
                  ;; dependency. Thus, let's pass commands in the ctx.
                  {:commands commands
                   :options options
                   :arguments arguments}

                (:marketplace options) (assoc :marketplace (:marketplace options))
                api-key (assoc :api-client (api-client/new-client api-key)))
          options (dissoc options :marketplace)]
      (<! (ensure-chan (handler options ctx))))))
