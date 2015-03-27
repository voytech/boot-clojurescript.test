(ns boot-clojurescript.test
  (:requrie [boot.core :as core]
            [adzerk.boot-cljs :refer  :all]
            [adzerk.boot-cljs.js-deps :as deps]
            [clojure.java.io :as io]
            [boot.util :refer [sh]]))

(defn edn2cljs [path]
  (.replaceAll path "\\.cljs.edn$" ".cljs"))

(core/deftask ^:private gen-tests-entry-point
  "Tasks generates test.cljs.edn which requires all other source namespaces pointed
   by other .edn file directives. "
  []
   (core/with-pre-wrap fileset
     (core/empty-dir! test-edn-dir)
     (let [{:keys [main]} (deps/scan-fileset fileset)
           test-edn-file (doto (io/file test-edn-dir "test.cljs.edn") (io/make-parents))]
       (when (seq main)
         (->> main
              (mapv #(comp symbol util/path->ns edn2cljs));;is util/path->ns worsk to edn ?
              (assoc {} :require)
              (spit test-edn-file))
         (-> fileset
             (core/add-source test-edn-dir)
             core/commit!))))
)

(core/deftask cljs-tests [ns namespaces str "A list of test namespaces to include in tests"]
  )

(core/deftask test-runner []
  (let [current-dir (System/getProperty "user.dir"),
        slimer-home (System/getenv "SLIMER_HOME")]
    (let [result (sh (str slimer-home "\\slimerjs.bat")
                     (str current-dir (get-env :cljs-runner))
                     (str current-dir "\\" (get-env :out-path)
                          "\\" (get-env :cljs-out-path)
                          "\\main.js"))]
      (println (:out result))
      )))

(core/deftask test []
  )
