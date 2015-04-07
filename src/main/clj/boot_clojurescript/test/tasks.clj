(ns boot-clojurescript.test.tasks
  (:require [boot.core :as core]
            [boot.file :as file]
            [boot.tmpdir :as tmpdir]
            [adzerk.boot-cljs :refer  :all]
            [adzerk.boot-cljs.js-deps :as deps]
            [clojure.java.io :as io]
            [clojure.walk :refer :all]
            [clojure.java.shell :as shell]
            [boot.util :as util])
 (:import
  [java.io File FileOutputStream]
  [java.util.zip ZipEntry ZipInputStream ZipException]
  [java.util.jar JarEntry JarInputStream Manifest Attributes$Name]))


(defn- time-stamp[]
  (str (System/currentTimeMillis)))

(def ^:private JS_TEST_FILE (str "test_" (time-stamp))) ;;must be unique across all namespaces

(defn assert-cljs [path]
  (if (re-matches #".+\.cljs.edn$" path)
    (.replaceAll path "\\.cljs.edn$" ".cljs")
    path))

(defn cljs-path->ns [path]
  (-> (assert-cljs path)
      (.replaceAll "\\.cljs$" ""))
)

(defn- fatal [message]
  (util/exit-error
   (do (util/fail message))))


(defn- test-paths[]
  (let [test-paths  (core/get-env :test-paths)]
    (if (not (nil? test-paths)) test-paths (fatal "Boot environment variable :test-paths is required to execute tests!"))))

(defn- test-paths-abs []
  (map #(str (System/getProperty "user.dir") "/" %)  (test-paths))
)

(core/deftask log-fileset [f fullpath FULLPATH bool "Print full path or just relative path? "]
  (core/with-pre-wrap fileset
    (println "current environment:")
    (println "----------------------------------------")
    (println (core/get-env))
    (println "----------------------------------------")
    (println "fileset :")
    (println "-----------------------------------------")
    (postwalk #(when (map? %) (when (contains? % :path) (println (str (when (= fullpath true) (:dir %)) "/" (:path %)))))
              fileset)
     fileset
))

(core/deftask make-shim-edn
  "Tasks generates .cljs.edn which requires all other source namespaces
   from fileset. This task creates a bootstrap shim to be places as script
   in html page."
  [n namespaces NAMESPACES #{sym} "A list of test namespaces to include in tests"
   f edn-name   EDNNAME      str  "Name of target cljs.edn file"
   e excludes   EXCLUDES   #{sym} "A list of namespaces to be excluded from shim. Currently not implemented."]
  (let [edn-dir (core/temp-dir!)
        file-name (if (not (nil? edn-name)) edn-name JS_TEST_FILE)]
    (core/with-pre-wrap fileset
      (util/info (str "Creating EDN - " file-name ".cljs.edn - entry point namespace shim file...\n"))
      (core/empty-dir! edn-dir)
      (let [{:keys [cljs]} (deps/scan-fileset fileset)
            edn-file (doto (io/file edn-dir (str file-name ".cljs.edn")) (io/make-parents))]
        (->> cljs
             (mapv #((comp symbol util/path->ns cljs-path->ns core/tmppath) %))
             (concat namespaces) ;;include ns for compilation if not included already
             (set)               ;;distinct elements required.
             (apply vector)      ;;I think .edn expects namespaces to be defined as vector.
             (assoc {} :require)
             (spit edn-file))
        (-> fileset
            (core/add-resource edn-dir) ;;I do not know what to choose add-source or add-resource
            core/commit!))))
  )

(core/deftask ^:private add-tests "Adds test sources to file set"
 []
 (let [test-dir (core/temp-dir!)]      ;; create temp-dir for test sources
     (core/with-pre-wrap fileset       ;; first append test namespaces to file set.
            (util/info "Adding test sources from :test-paths ...\n")
            (core/empty-dir! test-dir) ;; ensure it is empty
            (doseq [path (test-paths)]
              (file/copy-files path test-dir)) ;; just file system copy
            (-> fileset
                (core/add-source test-dir) ;; add test source directory to fileset
                core/commit!)                     ;; commit to fileset
            )))

(defn- absolute-tmp-path [file]
  (let [tmp? (partial satisfies? tmpdir/ITmpFile)]
    (if (tmp? file)
      (->> file
           ((juxt core/tmpdir core/tmppath))
           (clojure.string/join "/"))
      (.getPath file))))

(defn- file-tmp-path [inputs-files name]
  (->> inputs-files
       (core/by-name [name])
       first
       absolute-tmp-path))

(defn- file-at-temp-root [inputs-files name]
 (->> inputs-files
       (core/by-name [name])
       (filter #(= (str (core/tmpdir %) "/" (core/tmppath %))
                   (str (core/tmpdir %) "/" name)))
       first
       (absolute-tmp-path)))

(defn- make-executable [app]
  (shell/sh "chmod" "777" app)
)

(defn setup-slimer []
  (shell/sh "export SLIMERJSLAUNCHER=/usr/bin/firefox")
)

(defn- this-local-repo-location [mvn-repo]
  (str (if (nil? mvn-repo) (str (System/getenv "HOME") "/.m2/repository/") mvn-repo) "voytech/boot-clojurescript.test/0.1.0-SNAPSHOT/"  )
)
;;I think it is safe to assume we have got .m2 and repository folder somewhere.
;;For linux it should be in home dir of user, Need to handle other system cases.
;;In local repository there should be always artifact of this project installed.
;;we can take slimer resource from jar and copy it to working temp directory,
;;but I think we do not need slimerjs on fileset - so just copy to temp dir, remember temp
;; dir path and execute it.
;; boot-clojurescript.test-0.1.0-SHANPSHOT.jar

(defn- provide-launcher-in [execution-tmp-dir mvn-repo]
  (core/empty-dir! execution-tmp-dir)
  (let [jar-path (this-local-repo-location mvn-repo)]
    (file/copy-files jar-path execution-tmp-dir)
    (let [jars (core/by-ext [".jar"] (file-seq execution-tmp-dir))]
      (doseq [jar jars]
        (with-open [stream (JarInputStream. (io/input-stream jar))]
          (loop [jar-entry (.getNextEntry stream)]
            (when (not (nil? jar-entry))
              (do
                (let [entry-name (.getName jar-entry)
                      dest-entry (io/file execution-tmp-dir entry-name)]
                  (if (.isDirectory jar-entry)
                    (do
                      (.closeEntry stream))
                    (do
                      (io/make-parents dest-entry)
                      (with-open [output-stream (FileOutputStream. dest-entry)]
                        (io/copy stream output-stream)
                        (.closeEntry stream))))
                  (recur (.getNextEntry stream)))))))))))


(core/deftask launch-tests
  "Given fileset with compiled clojurescript sources,
   and path to compiled clojurescript shim for those sources
   (An entry point to put as script in page)
   launch test runner using lightweight slimerjs"
  [f firefox-path FIREFOXPATH str "A file system path to firefox binaries."
   n namespaces NAMESPACES #{sym} "A set of test namespaces"
   m maven-repo MAVENREPO str     "A path to maven repository to look for boot-clojurescript resources"]
  (let [exec-dir (core/temp-dir!)]
    (core/with-pre-wrap fileset
      (provide-launcher-in exec-dir maven-repo)
      (let [inputs (core/input-files fileset)
            m2-artifact (file-seq exec-dir)
            inputs-ext (concat inputs m2-artifact)
            test-engine     (file-tmp-path inputs-ext "slimerjs")   ;;hard-coded name! subject to improve!
            test-runner     (file-tmp-path inputs-ext "runner.js")  ;;hard-coded name! subject to improve!
            test-sources    (file-at-temp-root inputs (str JS_TEST_FILE ".js"))] ;;hard-coded name! subject to improve!
        (util/info "Launching tests...\n")
        (util/info (str "test launcher:" test-engine "\n"))
        (util/info (str "test runner:"   test-runner "\n"))
        (util/info (str "test sources:"  test-sources "\n"))
        (make-executable test-engine)
        ;;(setup-slimer)
        (let [result (shell/sh test-engine
                               test-runner
                               test-sources)]
          (println (:out result))
          )
        )
      fileset)))



;;TODO check if there would be possibility for cljs to compile only files
;;which were not yet compiled in current pipeline run.
;; there is core/rm methods which removes files from file set and returns new
;; fileset - this can be used for that purpose. Still we need to check
;; what was compiled.
(core/deftask cljs-tests
  "Adds test sources, creates entry namespace, compiles cljs, executes tests under slimerjs"
  [n namespaces NAMESPACES #{sym} "A list of test namespaces to include in tests"
   m maven-repo MAVENREPO str     "A path to maven repository to look for boot-clojurescript resources"]
    (comp
     (add-tests) ;; add test sources to fileset
     (make-shim-edn)      ;; generate edn files and add to file set
     (cljs)               ;; clojurescript compile with test namespaces appended.
     (launch-tests)       ;; run tests on slimerjs runner.
     )
)
