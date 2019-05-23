(ns sharetribe.flex-cli.commands.help
  (:require [sharetribe.flex-cli.render :as render]))

(defn p [content]
  [[:line {:format [:underline]} content]])

(defn heading [content]
  [[:line {:format [:white :bold]} content]])

(defn dt [content]
  [[heading content]])

(defn dd [content]
  [[:line "  " content]])

(defn section [title value]
  [[dt title]
   [dd value]])

(defn help [opts]
  (render/render
   [[p "CLI to interact with Sharetribe Flex"]
    [:br]
    [section
     "Version"
     "0.0.1"]
    [:br]
    [section
     "Usage"
     "$ flex [COMMAND]"]]))

(comment
  (sharetribe.flex-cli.core/main-dev-str "--help")
  )
