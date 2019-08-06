(ns sharetribe.flex-cli.command-spec
  (:require [clojure.spec.alpha :as s]))

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
