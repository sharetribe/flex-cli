(ns sharetribe.flex-cli.args-parse
  "Namespace for CLI argument parsing helpers.

  This namespace is unaware of the global command specifications. It
  expects that command-specs are passed as an argument.
  "
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.cli :as tools.cli]
            [sharetribe.flex-cli.commands :as commands]))

(defn- find-sub [sub-cmds name]
  (some #(when (= (:name %) name) %) sub-cmds))


(defn parse
  "Given command-line `args` and command spec `cmd`
  returns a map containing id and handler from the
  command spec and parsed options.

  Params:

  - `args` command-line arguments, coll of strings
  - `cmd` a command spec

  The command spec is a recursive tree structure supporting any number
  of subcommand levels.

  Returns:

  {:handler <command handler>
   :options {}
  }
  "
  [args cmd]
  (let [parse-result (tools.cli/parse-opts args (:opts cmd) :in-order true :strict true)
        {:keys [options arguments summary errors]} parse-result]

    (cond
      (seq errors) {:error :parse-error
                    :data {:errors errors}}

      (empty? arguments) {:handler (:handler cmd)
                          :options options}

      :else (if-let [sub-cmd (find-sub (:sub-cmds cmd) (first arguments))]
              (recur (rest arguments) sub-cmd)
              {:error :command-not-found
               :data {:arguments arguments}}))))

(s/def ::args coll?)

(s/fdef parse
  :args (s/cat :args ::args
               :cmd ::commands/root-cmd))
