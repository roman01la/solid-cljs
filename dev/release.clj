(ns release
  (:require [clojure.java.io :as io]
            [deps-library.release :as dl.release])
  (:import (org.apache.maven.model License)
           (org.apache.maven.model.io.xpp3 MavenXpp3Reader MavenXpp3Writer)))

(defn add-license []
  (let [pom-reader (MavenXpp3Reader.)
        pom-writer (MavenXpp3Writer.)
        file (io/file "pom.xml")
        model (->> (io/reader file)
                   (.read pom-reader))
        license (License.)]
    (.setName license "Eclipse Public License - Version 2.0")
    (.setUrl license "https://www.eclipse.org/legal/epl-2.0/")
    (.addLicense model license)
    (.write pom-writer (io/writer file) model)))

(alter-var-root #'dl.release/pom
                (fn [f]
                  (fn [& args]
                    (let [ret (apply f args)]
                      (add-license)
                      ret))))

(defn -main [& args]
  (apply dl.release/main args))
