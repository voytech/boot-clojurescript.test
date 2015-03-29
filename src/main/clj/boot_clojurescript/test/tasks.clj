(ns boot-clojurescript.test.tasks
  (:require [boot.core :as core]
            [boot.file :as file]
            [adzerk.boot-cljs :refer  :all]
            [adzerk.boot-cljs.js-deps :as deps]
            [clojure.java.io :as io]
            [boot.util :as util]))

(defn assert-cljs [path]
  (if (re-matches #".+\.cljs.edn$" path)
    (.replaceAll path "\\.cljs.edn$" ".cljs")
    path))

(defn- test-paths[]
  (core/get-env :test-paths))

(defn- test-paths-abs []
  (map #(str (System/getProperty "user.dir") "/" %)  (test-paths))
)

(core/deftask gen-test-edn
  "Tasks generates test.cljs.edn which requires all other source namespaces pointed
   by other.edn files. This task is a bootstrap for testing facility by
   providing edn which in turn helps to create loader script for test scripts."
 ;; []
  [n namespaces NAMESPACES #{sym} "A list of test namespaces to include in tests"]
   (core/with-pre-wrap fileset
     (let [test-edn-dir (core/temp-dir!)]
       (core/empty-dir! test-edn-dir)
       (let [{:keys [main cljs]} (deps/scan-fileset fileset)
             test-edn-file (doto (io/file test-edn-dir "test.cljs.edn") (io/make-parents))]
           (->> (if (seq main) main cljs)
                (mapv #(comp symbol util/path->ns assert-cljs core/tmppath));;is util/path->ns worsk to edn ?
                (concat namespaces)
                (assoc {} :require)
                (spit test-edn-file))
           (-> fileset
               (core/add-source test-edn-dir)
                core/commit!))))
)

(core/deftask scaffold[ns namespaces NAMESPACES #{sym} "A list of test namespaces to include in tests"]
;;Read file from resource .
;;replace script tag with ${LOADER_SCRIPT} with actual  loader script name
;;replace code ${EVAL_TESTS_BLOCK} with dynamic call to cemerick's clojurescript.test with namespaces.
)

(defn detach-compiled-files [fileset func]
   fileset
    )

(defn debug-tmpdir [tmpdir]
  (doseq [fl (file-seq (io/file tmpdir))]
    (println (str "*tmpdir* " fl))))

(core/deftask fileset-add-tests []
 (let [test-dir (core/temp-dir!)]   ;; create temp-dir for test sources
     (core/with-pre-wrap fileset   ;; first append test namespaces to file set.
            (core/empty-dir! test-dir) ;; ensure it is empty
            (doseq [path (test-paths)]
              (file/copy-files path test-dir)) ;; just file system copy
            (debug-tmpdir test-dir)
            (-> fileset
                (core/add-source test-dir) ;; add test source directory to fileset
                core/commit!)                     ;; commit to fileset
            )))

(defn absolute-tmp-path [file]
  (->> file
       ((juxt core/tmpdir core/tmppath))
       (clojure.string/join "/")))

(defn- file-tmp-path [inputs-fileset name]
  (->> inputs-fileset
       (core/by-name [name])
       first
       absolute-tmp-path))

(core/deftask test-runner
  [sv slimer-version VERSION str   "A version of slimer.js"
   ns namespaces NAMESPACES #{sym} "A set of test namespaces"]
  (core/with-pre-wrap fileset
    (let [inputs (core/input-files fileset)
          test-engine-dir (file-tmp-path inputs "slimerjs")
          test-runner-dir (file-tmp-path inputs "runner.js")
          test-sources    (file-tmp-path "test.js")]
      (println (str "test engine:" test-engine-dir))
      (println (str "test runner:" test-runner-dir))
      (println (str "test sources:" test-sources))
      (let [result (util/sh test-engine-dir
                            test-runner-dir
                            (apply str test-sources))]
        (println (:out result))
        )
      )))



;;TODO check if there would be possibility for cljs to compile only files
;;which were not yet compiled in current pipeline run.
;; there is core/rm methods which removes files from file set and returns new
;; fileset - this can be used for that purpose. Still we need to check
;; what was compiled.
(core/deftask cljs-tests
  "As long as there is test-paths environment variable test cljs sources and
   as long as test-paths are not included into fileset class-path by default,
   we have to add them to file-set classpath and compile them together with
   other sources. Rationale of introducing test-paths additionally to source-paths
   was to include test sources to class-path only for tests purposes. "
  [ns namespaces NAMESPACES #{sym} "A list of test namespaces to include in tests"]
    (comp
     (fileset-add-tests) ;; add test sources to fileset
     (gen-test-edn)      ;; generate edn files and add to file set
     (cljs)              ;; clojurescript compile with test namespaces appended.
     (test-runner)       ;; run tests on slimerjs runner.
     )
)
