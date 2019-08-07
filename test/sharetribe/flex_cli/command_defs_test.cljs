(ns sharetribe.flex-cli.command-defs-test
  (:require [clojure.spec.alpha :as s]
            [cljs.test :as t :refer-macros [deftest is testing]]
            [sharetribe.flex-cli.command-defs :as command-defs]
            [sharetribe.flex-cli.command-spec :as command-spec]))


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
                          :opts [command-defs/marketplace-opt]
                          :sub-cmds [{:name "list"
                                      :opts [{:id :process-name
                                              :long-opt "--process"}
                                             command-defs/marketplace-opt]}]}]}
             (command-defs/format-command-def cmd))))))

(deftest spec
  (testing "is valid"
    (is (nil? (s/explain-data ::command-spec/root-cmd command-defs/commands)))))
