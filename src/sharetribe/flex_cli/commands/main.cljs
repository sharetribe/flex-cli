(ns sharetribe.flex-cli.commands.main
  (:require [sharetribe.flex-cli.commands.help :as help]
            [sharetribe.flex-cli.commands.version :as version]))

(defn- main [options ctx]
  (cond
    ;; dissoc :help option because this is how the "help" command is
    ;; invoked
    (:help options) (help/help (dissoc options :help) ctx)
    (:version options) (version/version (dissoc options :version) ctx) ;; Same

    :else (help/help options ctx) ;; show help as a default
    ))
