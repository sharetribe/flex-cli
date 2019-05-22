(ns sharetribe.flex-cli.parse-test
  (:require [sharetribe.flex-cli.parse :as parse]
            [cljs.test :as t :refer-macros [deftest is]]))

(deftest main
  (let [cmd {:handler ::main-handler
             :opts [{:id :version
                     :short-opt "-V"
                     :long-opt "-v"}]}
        parse-result (parse/parse ["-V"] cmd [])]
    (is (= ::main-handler (:handler parse-result)))
    (is (= {:version true} (:options parse-result)))))

(deftest subcommands
  (let [cmd {:sub-cmds [{:name "process"
                         :handler ::process
                         :sub-cmds [{:name "list"
                                     :handler ::process-list}]}]}]
    (is (= ::process (:handler (parse/parse ["process"] cmd []))))
    (is (= ::process-list (:handler (parse/parse ["process" "list"] cmd []))))))

(deftest subcommands+opts
  (let [cmd {:sub-cmds [{:name "process"
                         :sub-cmds [{:name "list"
                                     :handler ::process-list
                                     :opts [{:id :process-name
                                             :long-opt "--process"
                                             :required "PROCESS NAME"}]}]}]}
        parse-result (parse/parse ["process" "list" "--process=nightly-booking"] cmd [])]
    (is (= ::process-list (:handler parse-result)))
    (is (= {:process-name "nightly-booking"} (:options parse-result)))))

(deftest subcommands+opts+global-opts
  (let [global-opts [{:id :marketplace
                      :long-opt "--marketplace"
                      :short-opt "-m"
                      :required "MARKETPLACE"}]
        cmd {:sub-cmds [{:name "process"
                         :sub-cmds [{:name "list"
                                     :handler ::process-list
                                     :opts [{:id :process-name
                                             :long-opt "--process"
                                             :required "PROCESS NAME"}]}]}]}
        parse-result (parse/parse ["process" "list" "--process=nightly-booking" "-m" "bike-soil"] cmd global-opts)]
    (is (= ::process-list (:handler parse-result)))
    (is (= {:process-name "nightly-booking"
            :marketplace "bike-soil"} (:options parse-result)))))

