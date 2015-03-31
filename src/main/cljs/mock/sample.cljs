(ns mock.sample)

(defn get-element-by-id []
  "not implemented")

(defn print-some-message [message]
  (.log js/window.console message))
