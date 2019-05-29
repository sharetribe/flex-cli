(ns sharetribe.flex-cli.commands-test
  (:require [sharetribe.flex-cli.commands :as commands]
            [cljs.test :as t :refer-macros [deftest is testing]]))


(deftest format-command-defs
  (testing "marketplace"
    (let [cmd {:no-marketplace? true
               :opts [{:id :help
                       :long-opt "--help"}]
               :sub-cmds [{:name "process"
                           :sub-cmds [{:name "list"
                                       :opts [{:id :process-name
                                               :long-opt "--process"}]}]}]}]
      (is (= {:no-marketplace? true
              :opts [{:id :help
                      :long-opt "--help"}]
              :sub-cmds [{:name "process"
                          :opts [commands/marketplace-opt]
                          :sub-cmds [{:name "list"
                                      :opts [{:id :process-name
                                              :long-opt "--process"}
                                             commands/marketplace-opt]}]}]}
             (commands/format-command-def cmd))))))
