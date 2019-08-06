(ns sharetribe.flex-cli.command-util
  "Namespace for util functions for accessing the command definition
  data structure.")

(defn subcommand
  "Returns a sub command by name from sub-cmds."
  [sub-cmds name]
  (first (filter #(= name (:name %)) sub-cmds)))

(defn subcommand-in
  "Traverses `cmd` structure and returns a subcommand by `path`."
  [cmd path]
  (reduce
   (fn [cmd name]
     (subcommand (:sub-cmds cmd) name))
   cmd
   path))
