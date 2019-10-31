(ns sharetribe.flex-cli.args-parse
  "Namespace for CLI argument parsing helpers.

  This namespace is unaware of the global command specifications. It
  expects that command-specs are passed as an argument.
  "
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.cli :as tools.cli]
            [sharetribe.flex-cli.command-spec :as command-spec]
            [sharetribe.flex-cli.exception :as exception]))

(defmethod exception/format-exception :command/parse-error [_ _ {:keys [errors]}]
  (str "Could not parse arguments:\n" (apply str (interpose "\n" errors))))

(defmethod exception/format-exception :command/not-found [_ _ {:keys [arguments]}]
  (str "Command not found: " (first arguments)))

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
   :no-api-key? <bool>
   :options {}
  }
  "
  [args cmd]
  (let [parse-result (tools.cli/parse-opts args (:opts cmd) :in-order true :strict true)
        {:keys [options arguments errors]} parse-result
        sub-cmd (find-sub (:sub-cmds cmd) (first arguments))
        finished? (or (empty? arguments)
                      (:catch-all? cmd))]

    (if finished?

      (cond
        ;; Parsing is finished. There were parse errors. Throw.
        (seq errors)
        (exception/throw! :command/parse-error {:errors errors})

        ;; Parsing is finished but we couldn't find command with a
        ;; handler. Throw command not found.
        (not (:handler cmd))
        (exception/throw! :command/not-found {:arguments [(:name cmd)]})

        ;; Parsing is finished, command found, no errors. Success!
        :else
        {:handler (:handler cmd)
         :no-api-key? (:no-api-key? cmd)
         :options options
         :arguments arguments})

      (cond
        ;; Parsing is not finished, but we couldn't find matching
        ;; subcommand. In addition, there were parse errors. Throw
        ;; parse-error instead of command not found, because most
        ;; likely the parse errors were the reason for the failuere.
        (and (not sub-cmd)
             errors)
        (exception/throw! :command/parse-error {:errors errors})

        ;; Parsing is not finished, but we couldn't find matching
        ;; subcommand.
        (not sub-cmd)
        (exception/throw! :command/not-found {:arguments arguments})

        ;; Continue parsing.
        :else
        (recur (rest arguments) sub-cmd)))))

(s/def ::args coll?)

(s/fdef parse
  :args (s/cat :args ::args
               :cmd ::command-spec/root-cmd))
