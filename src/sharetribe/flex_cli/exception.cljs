(ns sharetribe.flex-cli.exception
  "Utility namespace for global exception handling in the CLI.")

(defn throw!
  ([type] (throw! type nil))
  ([type data]
   (throw (ex-info (str type) {:type type, :data data}))))

(defmulti format-exception
  "Multimethod to format a given exception based on its type."
  (fn [type _ _ ] type))

(defn format-msg
  "Format the given exception (possibly thrown using exception/throw!)
  into a string."
  [e]
  (let [{:keys [type data]} (ex-data e)]
    (format-exception type (ex-message e) data)))

;; Formatters
;;

(defmethod format-exception :default [_ message data]
  (str message (if data (str "\n\n" (pr-str data)))))

(defmethod exception/format-exception :command/invalid-args [_ _ {:keys [command errors]}]
  (str "Invalid arguments for command " (name command) ":\n"
       (apply str (interpose "\n" errors))))
