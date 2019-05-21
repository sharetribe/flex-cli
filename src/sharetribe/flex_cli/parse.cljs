(ns sharetribe.flex-cli.parse
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.cli :as tools.cli]))

(defn- find-sub [sub-cmds name]
  (some #(when (= (:name %) name) %) sub-cmds))


(defn parse
  "Given command-line `args`, command spec `cmd` and global options spec
  `global-opts` returns a map containing id and handler from the
  command spec and parsed options.

  Params:

  - `args` command-line arguments, coll of strings
  - `cmd` a command spec
  - `global-opts` global opts spec

  The command spec is a recursive tree structure supporting any number
  of subcommand levels.

  Returns:

  {:handler <command handler>
   :options {}
  }
  "
  [args cmd global-opts]
  (let [with-global-opts (concat (:opts cmd) global-opts)
        parse-result (tools.cli/parse-opts args with-global-opts :in-order true)
        {:keys [options arguments summary errors]} parse-result]

    (cond
      (seq errors) {:error :parse-error
                    :data {:errors errors}}

      (empty? arguments) {:handler (:handler cmd)
                          :options options}

      :else (if-let [sub-cmd (find-sub (:sub-cmds cmd) (first arguments))]
              (recur (rest arguments) sub-cmd global-opts)
              {:error :command-not-found
               :data {:arguments arguments}}))))

(s/def ::args coll?)

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
(s/def ::sub-cmd (s/keys :req-un [::name]
                         :opt-un [::desc
                                  ::handler
                                  ::opts
                                  ::sub-cmds]))
(s/def ::root-cmd (s/keys :opt-un [::desc
                                   ::handler
                                   ::opts
                                   ::sub-cmds]))
(s/def ::global-opts ::opts)

(s/fdef parse
  :args (s/cat :args ::args
               :cmd ::root-cmd
               :global-opts ::global-opts))
