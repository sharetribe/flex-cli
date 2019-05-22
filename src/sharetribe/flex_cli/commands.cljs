(ns sharetribe.flex-cli.commands
  (:require [sharetribe.flex-cli.commands.auth :as auth]
            [sharetribe.flex-cli.commands.help :as help]
            [sharetribe.flex-cli.commands.marketplace :as marketplace]
            [sharetribe.flex-cli.commands.process :as process]
            [sharetribe.flex-cli.commands.version :as version]))

(def global-opts
  [{:id :marketplace
    :long-opt "--marketplace"
    :short-opt "-m"
    :required "MARKETPLACE IDENT"}])

(def commands
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
     :handler auth/login}
    {:name "logout"
     :handler auth/logout}
    {:name "marketplace"
     :handler marketplace/marketplace
     :sub-cmds
     [{:name "list"
       :handler marketplace/list}]}
    {:name "process"
     :handler process/process
     :opts [{:id :path
             :long-opt "--path"
             :required "[WIP] LOCAL PROCESS DIR"}
            {:id :process-name
             :long-opt "--process"
             :required "[WIP] PROCESS NAME"}
            {:id :version
             :long-opt "--version"
             :required "[WIP] VERSION NUM"}
            {:id :alias
             :long-opt "--alias"
             :required "[WIP] PROCESS ALIAS"}
            {:id :transition-name
             :long-opt "--transition"
             :required "[WIP] TRANSITION NAME"}]}]})

(defn parse-error [parse-result]
  (doseq [e (-> parse-result :data :errors)]
    (println e))
  {:exit-status 1})

(defn command-not-found [parse-result]
  (println "Command not found:" (-> parse-result :data :arguments first))
  {:exit-status 1})

(defn error [parse-result]
  (case (:error parse-result)
    :parse-error (parse-error parse-result)
    :command-not-found (command-not-found parse-result)))

(defn main [parse-result]
  (let [{:keys [options]} parse-result]
    (cond
      (:help options) (help/help (dissoc options :help)) ;; dissoc :help
                                                         ;; option
                                                         ;; because
                                                         ;; this is
                                                         ;; how
                                                         ;; the "help"
                                                         ;; command is
                                                         ;; invoked
      (:version options) (version/version (dissoc options :version)) ;; Same

      :else (help/help {}) ;; show help as a default

      )))

(defn handle [parse-result done]
  (let [result
        (cond
          (:error parse-result) (error parse-result)
          (= ::main (:handler parse-result)) (main parse-result)
          (:handler parse-result) ((:handler parse-result) (:options parse-result)))]
    (done result)))
