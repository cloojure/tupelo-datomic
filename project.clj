(defproject tupelo-datomic "0.9.4"
  :description "Tupelo Datomic:  Datomic With A Spoonful of Honey"
  :url "http://github.com/cloojure/tupelo-datomic"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [;[com.datomic/datomic-pro "0.9.5359" :exclusions [joda-time]]
                 ;[com.datomic/client-cloud "0.8.50"]
                 ;[com.datomic/datomic-free "0.9.5661"]

                 [cheshire "5.8.0"]
                 [criterium "0.4.4"]
                 [danlentz/clj-uuid "0.1.7"]
                 [enlive "1.1.6"]
                 ;[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/data.xml "0.2.0-alpha5"]
                 [org.clojure/spec.alpha "0.1.143"]
                 [org.clojure/test.check "0.9.0"]
                 ;[org.clojure/tools.logging "0.4.0"]
                 [prismatic/schema "1.1.7"]
                 [tupelo "0.9.71"]
                 ]
  :resource-paths ["resources/"
                   "resources/datomic-free-0.9.5661-everything.jar"
                  ]
  :profiles { :dev {:dependencies []
                   :plugins [] }
             :uberjar {:aot :all}}

  :global-vars { *warn-on-reflection* false }

; :repositories {"my.datomic.com" {:url "https://my.datomic.com/repo"
;                                  :creds :gpg}}

  :plugins  [ [lein-codox "0.10.3"] ]
  :codox {:src-dir-uri "http://github.com/cloojure/tupelo-datomic/blob/master/"
          :src-linenum-anchor-prefix "L"}
  :deploy-repositories {  "snapshots" :clojars
                          "releases"  :clojars 
                          :sign-releases false }
  :update :daily ;  :always
; :main ^:skip-aot tupelo-datomic.core
  :target-path "target/%s"
  :clean-targets [ "target" ]

  ; "lein test"         will not  run tests marked with the ":slow" metadata
  ; "lein test :slow"   will only run tests marked with the ":slow" metadata
  ; "lein test :all"    will run all  tests (built-in)
  :test-selectors { :default    (complement :slow)
                    :slow       :slow }

  :jvm-opts ["-Xms500m" "-Xmx2g"
            ;"--add-modules" "java.xml.bind"
            ;"-server"
            ]
)
